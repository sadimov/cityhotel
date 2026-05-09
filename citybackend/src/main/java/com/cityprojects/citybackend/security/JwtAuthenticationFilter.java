package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre JWT pour l'authentification des requetes.
 * <p>
 * Tour 3A : alimente {@link TenantContext} en plus du {@link SecurityContextHolder}
 * et du MDC. Garantit le nettoyage en {@code finally} pour eviter toute fuite
 * de contexte sur le pool de threads Tomcat (workers reutilises entre requetes).
 * <p>
 * Tour 7B C1 : durci pour interdire l'authentification d'un JWT valide sans
 * claim {@code hotelId} sauf pour le role {@link #GLOBAL_ROLE_SUPERADMIN}.
 * Auparavant, un JWT sans hotelId etait quand meme authentifie sans set du
 * TenantContext, ce qui ouvrait un mode "ROOT cross-tenant" sur tout endpoint
 * non protege par {@code @RequireTenant}. Desormais :
 * <ul>
 *   <li>JWT valide + hotelId non null -&gt; auth + TenantContext set ;</li>
 *   <li>JWT valide + hotelId null + role SUPERADMIN -&gt; auth, PAS de
 *       TenantContext (mode ROOT explicite et trace en INFO) ;</li>
 *   <li>JWT valide + hotelId null + role autre -&gt; <b>401 Unauthorized</b>,
 *       chaine interrompue, AUCUNE authentification posee.</li>
 * </ul>
 * Pas de header Authorization -&gt; chaine continue (cas /auth/login, /actuator).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String MDC_USER_ID = "user_id";
    private static final String MDC_HOTEL_ID = "hotel_id";
    private static final String MDC_ROLE = "role";

    /**
     * Roles globaux autorises a posseder un JWT sans claim {@code hotelId}.
     * Tout autre role sans hotelId est rejete en 401. Garde la liste minimale :
     * SUPERADMIN est le seul role legitime cross-tenant aujourd'hui (gestion des
     * hotels, deverrouillage de comptes globaux). Les futurs roles transverses
     * (ex. SYSTEM_AUDITOR) doivent etre ajoutes ici explicitement.
     */
    private static final String GLOBAL_ROLE_SUPERADMIN = "SUPERADMIN";

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (!StringUtils.hasText(jwt)) {
                // Pas de header : la chaine continue, le SecurityContext reste vide.
                // Spring Security renverra 401 / 403 en aval si l'endpoint l'exige.
                filterChain.doFilter(request, response);
                return;
            }

            if (!tokenProvider.validateToken(jwt)) {
                // JWT present mais invalide (signature, expiration, malforme) :
                // ne pas authentifier, ne pas continuer la chaine, retourner 401
                // immediatement. AuthenticationEntryPoint formate la reponse JSON.
                logger.warn("JWT present mais invalide — rejet 401");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid jwt");
                return;
            }

            Long userId = tokenProvider.getUserIdFromJWT(jwt);
            Long hotelId = tokenProvider.getHotelIdFromJWT(jwt);
            String roleCode = tokenProvider.getRoleCodeFromJWT(jwt);

            // Cas 3 (Tour 7B C1) : JWT valide sans hotelId pour un role non-global
            // -> rejet 401 IMMEDIAT. Sans cette garde, on tomberait en mode ROOT
            // implicite sur tout endpoint qui n'aurait pas @RequireTenant.
            if (hotelId == null && !GLOBAL_ROLE_SUPERADMIN.equals(roleCode)) {
                logger.warn("JWT valide sans hotelId pour role non-global '{}' (userId={}) — rejet 401",
                        roleCode, userId);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "tenant context required for non-superadmin");
                return;
            }

            // MDC : alimente avant l'auth pour tracer aussi les eventuelles erreurs
            // levees par loadUserById (UsernameNotFoundException, etc.).
            if (userId != null) {
                MDC.put(MDC_USER_ID, userId.toString());
            }
            if (roleCode != null) {
                MDC.put(MDC_ROLE, roleCode);
            }

            if (hotelId != null) {
                // Cas 1 : JWT tenant-scoped normal.
                MDC.put(MDC_HOTEL_ID, hotelId.toString());
                TenantContext.set(hotelId);
                logger.debug("JWT authentifie tenant-scoped (userId={}, hotelId={}, role={})",
                        userId, hotelId, roleCode);
            } else {
                // Cas 2 : super-admin sans tenant. AUCUN appel a TenantContext.set
                // — le thread reste en mode ROOT pour la duree de la requete.
                // La trace INFO permet de filtrer ces appels en prod (audit
                // securitaire des actions cross-tenant).
                logger.info("JWT super-admin sans tenant — mode ROOT (userId={})", userId);
            }

            try {
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                // Probleme de chargement utilisateur (ex. compte verrouille apres
                // emission du JWT) : on logge mais on laisse passer la chaine
                // SANS authentification. Spring Security retournera 401/403 en aval.
                logger.error("Impossible de charger l'utilisateur (userId={}) — auth non posee", userId, ex);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Nettoyage IMPERATIF en fin de requete : Tomcat reutilise les workers,
            // sans cela les logs d'une requete suivante porteraient l'identite
            // (et le tenant) d'un autre hotel.
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_HOTEL_ID);
            MDC.remove(MDC_ROLE);
            TenantContext.clear();
            // SecurityContextHolder est nettoye par le filter standard de Spring
            // Security (SecurityContextHolderFilter), inutile de le faire ici.
        }
    }

    /**
     * Extrait le token JWT de la requete.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
