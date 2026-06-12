package com.cityprojects.citybackend.security;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.service.hebergement.NightAuditLockRegistry;
import com.cityprojects.citybackend.util.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtre HTTP qui bloque les opérations modifiantes pendant l'exécution du
 * night audit (cf. {@code règles_night_audit.txt} §6).
 *
 * <h3>Comportement</h3>
 * <p>Pour chaque requête, si toutes les conditions suivantes sont vraies :</p>
 * <ul>
 *   <li>Méthode HTTP modifiante ({@code POST, PUT, PATCH, DELETE}) — les
 *       lectures {@code GET, HEAD, OPTIONS} restent toujours autorisées.</li>
 *   <li>Le path ne fait pas partie des exclusions (auth, endpoint d'exécution
 *       du NA lui-même).</li>
 *   <li>Un tenant est positionné dans {@link TenantContext} (le filtre passe
 *       transparently si pas de tenant — cas SUPERADMIN root ou requête
 *       anonyme déjà rejetée par {@code JwtAuthenticationFilter}).</li>
 *   <li>Le tenant est marqué verrouillé dans le {@link NightAuditLockRegistry}.</li>
 * </ul>
 * <p>...alors la requête est rejetée en {@code 423 Locked} avec un payload
 * JSON {@link ApiResponse} portant la clé i18n {@code error.nightAudit.inProgress}.</p>
 *
 * <h3>Ordre du filtre</h3>
 * <p>Doit s'exécuter <b>après</b> {@link JwtAuthenticationFilter} pour avoir
 * {@code TenantContext} positionné. Enregistré dans {@code SecurityConfig}
 * via {@code http.addFilterAfter(nightAuditLockFilter, JwtAuthenticationFilter.class)}.</p>
 *
 * <h3>Alerte 3 min — non bloquante</h3>
 * <p>Précision métier : l'alerte SSE à T-3min affiche un toast mais
 * <b>n'active pas</b> le verrou. Le verrou ne s'active que lorsqu'un
 * utilisateur clique « Lancer la clôture » et que {@code NightAuditService.run()}
 * transitionne la journée hôtelière en CLOTURE_EN_COURS. Les 3 minutes
 * permettent aux opérateurs de finir leurs opérations en cours.</p>
 */
@Component
public class NightAuditLockFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditLockFilter.class);

    /** Méthodes HTTP non bloquées (lectures). */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Chemins exemptés : auth (login, refresh, logout) + endpoint /run du NA
     * lui-même (sinon impossible de débloquer la situation).
     *
     * <p>Note : on garde la sortie du NA accessible, ainsi que les flux SSE
     * de notifications (souscription = GET, déjà filtré par READ_METHODS,
     * mais on liste le path pour documentation).</p>
     */
    private static final String[] EXCLUDED_PATH_PREFIXES = {
            "/auth/",
            "/api/auth/",
            "/api/hebergement/night-audit/run",
            "/actuator/health"
    };

    private final NightAuditLockRegistry lockRegistry;
    private final ObjectMapper objectMapper;

    public NightAuditLockFilter(NightAuditLockRegistry lockRegistry,
                                ObjectMapper objectMapper) {
        this.lockRegistry = lockRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldBypass(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long tenantId = TenantContext.getOrNull();
        if (tenantId == null) {
            // Pas de tenant positionné (SUPERADMIN root, requête anonyme déjà
            // rejetée plus tôt par JwtAuthenticationFilter) : on laisse passer.
            filterChain.doFilter(request, response);
            return;
        }

        if (lockRegistry.isLocked(tenantId)) {
            logger.info("Night audit en cours pour hotelId={} — write {} {} rejeté en 423",
                    tenantId, request.getMethod(), request.getRequestURI());
            writeLockedResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * {@code true} si la requête ne doit pas être inspectée par ce filtre
     * (méthode read-only ou chemin exempté).
     */
    private boolean shouldBypass(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || READ_METHODS.contains(method.toUpperCase())) {
            return true;
        }
        String path = request.getRequestURI();
        // request.getRequestURI() inclut le context-path /citybackend ; on
        // matche sur les suffixes connus pour ne pas dépendre du context.
        if (path == null) return false;
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sérialise un {@link ApiResponse} d'erreur portant la clé i18n
     * {@code error.nightAudit.inProgress} et le status 423.
     */
    private void writeLockedResponse(HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.LOCKED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> payload = ApiResponse.error(
                "error.nightAudit.inProgress",
                HttpStatus.LOCKED.value());
        payload.setPath(request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }
}
