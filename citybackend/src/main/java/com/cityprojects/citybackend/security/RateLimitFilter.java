package com.cityprojects.citybackend.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter sur les endpoints d'authentification (Tour 38 C10).
 *
 * <p>Protege {@code /auth/login} et {@code /auth/refresh} contre le brute-force et
 * le credential-stuffing. 1 instance {@link RateLimiter} par IP, 10 req / 60s.
 * Au-dela, retourne 429 Too Many Requests + cle i18n {@code error.rateLimit.exceeded}.</p>
 *
 * <h3>Pourquoi Resilience4j et pas Bucket4j</h3>
 * <p>Resilience4j est deja au pom (artifact {@code resilience4j-spring-boot3} 2.2.0)
 * pour les besoins Dolibarr. Reutiliser cette dependance evite d'introduire
 * Bucket4j et son ecosysteme. La config par defaut "auth-endpoints" est fournie
 * via {@code application.yml} sous {@code resilience4j.ratelimiter.instances}.</p>
 *
 * <h3>Cache des RateLimiter par IP</h3>
 * <p>{@link ConcurrentHashMap} simple - une entree par IP source. Pas d'eviction
 * automatique : la quantite d'IP attaquantes etant bornee en pratique (DoS sur
 * /auth/* est dej a un signal d'alerte), la croissance memoire reste acceptable.
 * Si besoin futur d'eviction, passer a Caffeine.</p>
 *
 * <h3>Place dans la chaine</h3>
 * <p>Enregistre AVANT {@link JwtAuthenticationFilter} dans
 * {@link com.cityprojects.citybackend.config.SecurityConfig}. Le rejet 429
 * doit court-circuiter la validation JWT (sinon l'attaquant peut amplifier la
 * charge CPU via un parse JJWT a chaque requete).</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Endpoints proteges (path SANS context-path /citybackend). */
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/auth/login",
            "/auth/refresh"
    );

    private final RateLimiterRegistry registry;
    private final ConcurrentHashMap<String, RateLimiter> limitersByIp = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // getServletPath peut etre "" sous MockMvc / certaines configurations Tomcat ;
        // utiliser getRequestURI() (sans context-path) garantit le match.
        String path = resolvePath(request);
        if (!RATE_LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        RateLimiter limiter = limitersByIp.computeIfAbsent(ip, this::createLimiterForIp);

        if (!limiter.acquirePermission()) {
            logger.warn("Rate limit depasse pour IP={} sur {} - 429 Too Many Requests", ip, path);
            // Jakarta Servlet n'expose pas SC_TOO_MANY_REQUESTS en constante — literal 429.
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"success\":false,\"error\":\"error.rateLimit.exceeded\","
              + "\"status\":429,\"path\":\"" + path + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Cree un {@link RateLimiter} dedie a une IP avec la config "auth-endpoints"
     * (definie dans application.yml). Si cette config n'est pas trouvee, fallback
     * defensif sur 10 req / 60s.
     */
    private RateLimiter createLimiterForIp(String ip) {
        try {
            RateLimiterConfig base = registry.getConfiguration("auth-endpoints")
                    .orElseGet(() -> RateLimiterConfig.custom()
                            .limitForPeriod(10)
                            .limitRefreshPeriod(Duration.ofSeconds(60))
                            .timeoutDuration(Duration.ZERO)
                            .build());
            return RateLimiter.of("auth-" + ip, base);
        } catch (RuntimeException e) {
            logger.error("Impossible de creer le RateLimiter pour IP={} - fallback minimal", ip, e);
            return RateLimiter.of("auth-" + ip, RateLimiterConfig.custom()
                    .limitForPeriod(10)
                    .limitRefreshPeriod(Duration.ofSeconds(60))
                    .timeoutDuration(Duration.ZERO)
                    .build());
        }
    }

    /**
     * Resout le path de la requete en privilegiant {@code getRequestURI()} (sans
     * context-path) avec fallback sur {@code getServletPath()}. Sous MockMvc,
     * {@code getServletPath()} peut etre vide ; sous Tomcat reel les deux sont
     * equivalents sur les paths sans context-path.
     */
    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return request.getServletPath();
        }
        // Strip context-path si present (Tomcat reel le prefixe avec /citybackend).
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * Extrait l'IP client en respectant X-Forwarded-For (cas reverse proxy).
     * On prend la PREMIERE IP de la liste (la plus a gauche) qui est l'origine.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
