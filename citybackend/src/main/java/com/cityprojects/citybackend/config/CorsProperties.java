package com.cityprojects.citybackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Propriétés CORS chargées depuis {@code app.cors.*} (YAML / env).
 *
 * <h2>Pourquoi {@code @ConfigurationProperties} et plus {@code @Value} ?</h2>
 * <p>{@code @Value("${app.cors.allowed-methods:...}") List<String>} ne mappe
 * <b>pas</b> les listes YAML : Spring résout le placeholder en string, puis
 * tente un split CSV ; si la propriété est définie comme liste YAML
 * ({@code - GET}, {@code - POST}, ...), Spring tombe sur le default codé en
 * dur. Bug constaté au Tour audit PATCH/CORS — les YAML listaient PATCH mais
 * Spring restait sur l'ancien default {@code GET,POST,PUT,DELETE,OPTIONS},
 * et le preflight PATCH était rejeté en {@code 403 "Invalid CORS request"}.</p>
 *
 * <p>{@code @ConfigurationProperties} fait la liaison directement via le
 * binder Spring Boot qui sait lire les listes YAML.</p>
 *
 * <h2>Périmètre de la migration</h2>
 * <p>Seules les 4 propriétés historiquement portées par {@code @Value} sont
 * migrées : {@code allowedOrigins}, {@code allowedMethods},
 * {@code allowCredentials}, {@code maxAge}. Les listes
 * {@code allowedHeaders} et {@code exposedHeaders} restent codées en dur
 * dans {@code SecurityConfig#corsConfigurationSource} : les YAML existants
 * déclarent {@code allowed-headers: ["*"]}, valeur incompatible avec
 * {@code allowCredentials=true} en Spring Security (rejet immédiat). Pour
 * étendre la migration à ces deux listes, nettoyer d'abord les 3
 * {@code application*.yml} (retirer la clé ou expliciter la whitelist).</p>
 */
@ConfigurationProperties("app.cors")
public class CorsProperties {

    /** Origines autorisées (whitelist explicite, jamais {@code *} avec credentials). */
    private List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:4200"));

    /** Méthodes HTTP autorisées par le preflight CORS. */
    private List<String> allowedMethods = new ArrayList<>(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    /** Autorise les credentials (cookies / Authorization). */
    private boolean allowCredentials = true;

    /** Durée de cache du preflight (secondes). */
    private long maxAge = 3600L;

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public List<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }

    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
}
