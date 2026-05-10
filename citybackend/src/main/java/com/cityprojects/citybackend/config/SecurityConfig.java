package com.cityprojects.citybackend.config;

import com.cityprojects.citybackend.security.JwtAuthenticationEntryPoint;
import com.cityprojects.citybackend.security.JwtAuthenticationFilter;
import com.cityprojects.citybackend.security.RateLimitFilter;
import com.cityprojects.citybackend.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.Arrays;
import java.util.List;

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
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    // Configuration CORS avec valeurs par defaut
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private List<String> allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

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

                // /error est public mais NE doit JAMAIS embarquer le MDC dans le payload
                // reponse (ni hotelId, ni user_id). Verifie dans JwtAuthenticationEntryPoint
                // et GlobalExceptionHandler (audit Tour 7B I3 : OK au 2026-05-05).
                .requestMatchers("/error").permitAll()

                // Tour 38 C5 : tout admin sous /api/admin/** (single source of truth).
                // Les anciennes regles dispersees /admin/hotels, /admin/users, /admin/roles
                // ne matchaient rien (tous les controllers exposent /api/admin/...).
                .requestMatchers("/api/admin/**").hasRole("SUPERADMIN")

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
                .requestMatchers("/profile/**").authenticated()

                // Toutes les autres requetes necessitent une authentification
                .anyRequest().authenticated()
            );

        http.authenticationProvider(daoAuthenticationProvider());
        // Tour 38 C10 : RateLimitFilter AVANT le JwtAuthenticationFilter ; les rejets
        // 429 doivent court-circuiter avant la validation JWT (sinon DoS via JWT parse).
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuration CORS - Tour 38 H2 : whitelist headers explicite, plus de fallback '*'.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origines : doivent etre fournies, sinon default explicite (pas de '*').
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
        } else {
            configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:4200"
            ));
        }

        if (allowedMethods != null && !allowedMethods.isEmpty()) {
            configuration.setAllowedMethods(allowedMethods);
        } else {
            configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
            ));
        }

        // Tour 38 H2 : whitelist explicite, plus de '*'. Refuser tout header
        // non liste (Origin, Authorization, Content-Type, X-Requested-With,
        // Accept-Language pour i18n).
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept-Language"
        ));

        // Headers exposes au front (lecture cote JS).
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Total-Count"
        ));

        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

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
