package com.cityprojects.citybackend.config;

import com.cityprojects.citybackend.security.JwtAuthenticationEntryPoint;
import com.cityprojects.citybackend.security.JwtAuthenticationFilter;
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
 * Configuration de sécurité pour l'application
 * Version corrigée avec valeurs par défaut
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

    // Configuration CORS avec valeurs par défaut
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private List<String> allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private List<String> allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    /**
     * Configuration du filtre de sécurité principal
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                // Endpoints d'authentification publics (Tour 7B I1) :
                // login + refresh sont les SEULS appels anonymes acceptes ; tout le reste
                // de /auth/** exige authentification + role explicite (cf. AuthController).
                .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                .requestMatchers("/auth/sessions/stats").hasRole("SUPERADMIN")
                .requestMatchers("/auth/**").authenticated()

                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // /error est public mais NE doit JAMAIS embarquer le MDC dans le payload
                // reponse (ni hotelId, ni user_id). Verifier JwtAuthenticationEntryPoint
                // et GlobalExceptionHandler ne fuitent rien (audit Tour 7B I3 : OK au
                // 2026-05-05, aucun MDC.getCopyOfContextMap() ni hotelId/userId n'est
                // remonte dans les bodies d'erreur).
                .requestMatchers("/error").permitAll()
                
                // Endpoints d'administration - SUPERADMIN uniquement
                .requestMatchers("/admin/hotels/**").hasRole("SUPERADMIN")
                .requestMatchers("/admin/users/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers("/admin/roles/**").hasRole("SUPERADMIN")
                
                // Endpoints de gestion des hôtels - ADMIN et GERANT
                .requestMatchers("/hotels/**").hasAnyRole("SUPERADMIN", "ADMIN", "GERANT")
                
                // Endpoints de réservation - Rôles métier
                .requestMatchers("/reservations/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION", "RESREC")
                
                // Endpoints clients et sociétés
                .requestMatchers("/clients/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION", "RESREC")
                .requestMatchers("/societes/**").hasAnyRole("ADMIN", "GERANT", "RECEPTION")
                
                // Endpoints restaurant
                .requestMatchers("/restaurant/**").hasAnyRole("ADMIN", "GERANT", "RESTAURANT", "RESREC")
                
                // Endpoints stocks/inventory
                .requestMatchers("/inventory/**").hasAnyRole("ADMIN", "GERANT")
                
                // Endpoints ménage
                .requestMatchers("/menage/**").hasAnyRole("ADMIN", "GERANT")
                
                // Endpoints finance
                .requestMatchers("/finance/**").hasAnyRole("ADMIN", "GERANT")
                
                // Endpoints reporting
                .requestMatchers("/reporting/**").hasAnyRole("ADMIN", "GERANT")
                
                // Profile accessible à tous les utilisateurs authentifiés
                .requestMatchers("/profile/**").authenticated()
                
                // Toutes les autres requêtes nécessitent une authentification
                .anyRequest().authenticated()
            );

        http.authenticationProvider(daoAuthenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    /**
     * Configuration CORS avec gestion des erreurs
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        try {
            // Configuration des origines autorisées
            if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                configuration.setAllowedOrigins(allowedOrigins);
            } else {
                // Valeurs par défaut si la configuration échoue
                configuration.setAllowedOrigins(Arrays.asList(
                    "http://localhost:3000", 
                    "http://localhost:4200"
                ));
            }
            
            // Configuration des méthodes
            if (allowedMethods != null && !allowedMethods.isEmpty()) {
                configuration.setAllowedMethods(allowedMethods);
            } else {
                configuration.setAllowedMethods(Arrays.asList(
                    "GET", "POST", "PUT", "DELETE", "OPTIONS"
                ));
            }
            
            // Configuration des headers
            if (allowedHeaders != null && !allowedHeaders.isEmpty()) {
                configuration.setAllowedHeaders(allowedHeaders);
            } else {
                configuration.setAllowedHeaders(Arrays.asList("*"));
            }
            
            // Headers exposés
            configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Total-Count"
            ));
            
            configuration.setAllowCredentials(allowCredentials);
            configuration.setMaxAge(maxAge);
            
        } catch (Exception e) {
            // Configuration de secours en cas d'erreur
            configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("*"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
        }
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Encodeur de mots de passe BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Gestionnaire d'authentification
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Provider d'authentification DAO
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Registre de sessions pour gérer les sessions concurrentes
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}