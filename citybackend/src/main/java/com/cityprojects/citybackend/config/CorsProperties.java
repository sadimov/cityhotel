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
 * <p>Tous les champs CORS configurables sont liés ici : {@code allowedOrigins},
 * {@code allowedMethods}, {@code allowedHeaders}, {@code exposedHeaders},
 * {@code allowCredentials}, {@code maxAge}. Defaults Java prudents (whitelist
 * explicite, jamais {@code *} avec credentials).</p>
 *
 * <p><b>Important</b> : la valeur {@code "*"} dans {@code allowed-headers}
 * <b>n'est pas supportée</b> par Spring Security quand {@code allowCredentials=true}
 * (le bean refuse de se construire et le démarrage échoue avec une erreur
 * explicite). Les 3 {@code application*.yml} doivent lister la whitelist
 * explicite, jamais {@code "*"}.</p>
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

    /**
     * Headers acceptés sur la requête entrante. Tour 38 H2 : whitelist
     * explicite, jamais {@code "*"} (incompatible avec credentials).
     * {@code Accept-Language} pour i18n côté ngx-translate / locale serveur.
     */
    private List<String> allowedHeaders = new ArrayList<>(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept-Language"));

    /** Headers exposés au front (lecture côté JS via fetch/XHR). */
    private List<String> exposedHeaders = new ArrayList<>(Arrays.asList(
            "Authorization", "Content-Type", "X-Total-Count"));

    /** Autorise les credentials (cookies / Authorization). */
    private boolean allowCredentials = true;

    /** Durée de cache du preflight (secondes). */
    private long maxAge = 3600L;

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public List<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

    public List<String> getAllowedHeaders() { return allowedHeaders; }
    public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }

    public List<String> getExposedHeaders() { return exposedHeaders; }
    public void setExposedHeaders(List<String> exposedHeaders) { this.exposedHeaders = exposedHeaders; }

    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }

    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
}
