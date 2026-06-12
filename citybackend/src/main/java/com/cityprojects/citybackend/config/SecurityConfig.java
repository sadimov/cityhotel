package com.cityprojects.citybackend.config;

import com.cityprojects.citybackend.security.JwtAuthenticationEntryPoint;
import com.cityprojects.citybackend.security.JwtAuthenticationFilter;
import com.cityprojects.citybackend.security.NightAuditLockFilter;
import com.cityprojects.citybackend.security.RateLimitFilter;
import com.cityprojects.citybackend.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


/**
 * Configuration de securite pour l'application.
 *
 * <p>Tour 38 hardening :
 * <ul>
 *   <li>C4 : actuator/health public, reste actuator reserve SUPERADMIN.</li>
 *   <li>C5 : tout admin sous /api/admin/** (single source of truth).</li>
 *   <li>C10 : RateLimitFilter avant JwtAuthenticationFilter pour /auth/login + /auth/refresh.</li>
 *   <li>H1 : HSTS + CSP minimal + Referrer-Policy.</li>
 *   <li>H2 : whitelist headers explicite, fallback CORS '*' supprime.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private NightAuditLockFilter nightAuditLockFilter;

    /**
     * Configuration CORS chargée depuis {@code app.cors.*} via
     * {@link CorsProperties} (@ConfigurationProperties — lit correctement
     * les listes YAML, contrairement à @Value qui était utilisé auparavant).
     */
    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * Configuration du filtre de securite principal.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // Tour 38 H1 : headers de securite minimaux (HSTS, CSP, Referrer-Policy).
            // Pas de XContentTypeOptions ni X-Frame-Options ici : Spring Security les
            // ajoute par defaut.
            .headers(h -> h
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )
            .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                // Endpoints d'authentification publics (Tour 7B I1) :
                // login + refresh sont les SEULS appels anonymes acceptes ; tout le reste
                // de /auth/** exige authentification + role explicite (cf. AuthController).
                .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                .requestMatchers("/auth/sessions/stats").hasRole("SUPERADMIN")
                .requestMatchers("/auth/**").authenticated()

                // Tour 38 C4 : seul /actuator/health est public (probe Kubernetes).
                // Tout le reste de l'actuator est reserve SUPERADMIN.
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("SUPERADMIN")

                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Tour A : avatars servis en static (URL contient UUID, peu devinable).
                // L'image n'est pas une donnee sensible ; la garde sur la mutation reste
                // /api/profile/me/avatar (auth obligatoire).
                .requestMatchers("/uploads/avatars/**").permitAll()

                // /error est public mais NE doit JAMAIS embarquer le MDC dans le payload
                // reponse (ni hotelId, ni user_id). Verifie dans JwtAuthenticationEntryPoint
                // et GlobalExceptionHandler (audit Tour 7B I3 : OK au 2026-05-05).
                .requestMatchers("/error").permitAll()

                // Exception consigne user 2026-05-21 : le referentiel des roles
                // /api/admin/roles doit etre lisible par ADMIN aussi (formulaire
                // "Mon hotel > Nouvel utilisateur" alimente son select role via
                // cet endpoint). Le @PreAuthorize du RoleAdminController autorise
                // deja SUPERADMIN + ADMIN, mais la regle filterChain ci-dessous
                // (/api/admin/**) prevaut et renvoyait un 403 avant evaluation.
                // L'exception DOIT precede la regle generale (ordre d'evaluation).
                .requestMatchers("/api/admin/roles", "/api/admin/roles/**")
                    .hasAnyRole("SUPERADMIN", "ADMIN")

                // Tour 38 C5 : tout admin sous /api/admin/** (single source of truth).
                // Les anciennes regles dispersees /admin/hotels, /admin/users, /admin/roles
                // ne matchaient rien (tous les controllers exposent /api/admin/...).
                .requestMatchers("/api/admin/**").hasRole("SUPERADMIN")

                // Tour A : ADMIN d'hotel gere ses users via /api/hotel/users/**.
                // SUPERADMIN inclus pour cohrence (en pratique il passe par /api/admin/...).
                // Le tenant est resolu par TenantContext cote service (jamais path-param).
                .requestMatchers("/api/hotel/**").hasAnyRole("ADMIN", "SUPERADMIN")

                // Endpoints de gestion des hotels - ADMIN et GERANT
                .requestMatchers("/hotels/**").hasAnyRole("SUPERADMIN", "ADMIN", "GERANT")

                // Endpoints de reservation - Roles metier
                .requestMatchers("/reservations/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION", "RESREC")

                // Endpoints clients et societes
                .requestMatchers("/clients/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION", "RESREC")
                .requestMatchers("/societes/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION")

                // Endpoints restaurant
                .requestMatchers("/restaurant/**").hasAnyRole("ADMIN", "GERANT", "RESTAURANT", "RESREC")

                // Endpoints stocks/inventory
                .requestMatchers("/inventory/**").hasAnyRole("ADMIN", "GERANT")

                // Endpoints menage
                .requestMatchers("/menage/**").hasAnyRole("ADMIN", "GERANT")

                // Endpoints finance
                .requestMatchers("/finance/**").hasAnyRole("ADMIN", "GERANT")

                // Endpoints reporting
                .requestMatchers("/reporting/**").hasAnyRole("ADMIN", "GERANT")

                // Profile accessible a tous les utilisateurs authentifies
                // (Tour A : /api/profile/** ajoute pour le self-service ; ancien
                // /profile/** conserve pour compat avec d'anciens clients tests).
                .requestMatchers("/profile/**", "/api/profile/**").authenticated()

                // Toutes les autres requetes necessitent une authentification
                .anyRequest().authenticated()
            );

        http.authenticationProvider(daoAuthenticationProvider());
        // Tour 38 C10 : RateLimitFilter AVANT le JwtAuthenticationFilter ; les rejets
        // 429 doivent court-circuiter avant la validation JWT (sinon DoS via JWT parse).
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // NightAuditLockFilter APRÈS JwtAuthenticationFilter : on a besoin de
        // TenantContext positionné pour décider si l'hôtel est en clôture.
        // Rejette les writes en 423 Locked pendant CLOTURE_EN_COURS.
        http.addFilterAfter(nightAuditLockFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuration CORS — Tour 38 H2 : whitelist headers explicite, plus de
     * fallback '*'. Tous les champs sont liés à {@link CorsProperties}
     * (binder Spring Boot natif, lit correctement les listes YAML).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Encodeur de mots de passe BCrypt (cost 12).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Gestionnaire d'authentification.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Provider d'authentification DAO.
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Registre de sessions pour gerer les sessions concurrentes.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}
