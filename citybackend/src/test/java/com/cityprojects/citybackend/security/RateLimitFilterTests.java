package com.cityprojects.citybackend.security;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire de {@link RateLimitFilter} (Tour 38 C10).
 *
 * <p>Ne mocke PAS Resilience4j : utilise un vrai {@link RateLimiterRegistry}
 * pour tester le comportement integral. Les tests utilisent un quota tres bas
 * (2 req/min) pour declencher le 429 facilement.</p>
 */
class RateLimitFilterTests {

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CountingChain chain;

    @BeforeEach
    void setUp() {
        // Quota tres bas pour saturer immediatement.
        RateLimiterConfig cfg = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(cfg);
        // Enregistre une config nommee "auth-endpoints" — celle utilisee par le filtre.
        registry.addConfiguration("auth-endpoints", cfg);

        filter = new RateLimitFilter(registry);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new CountingChain();
    }

    @Test
    @DisplayName("Path NON limite : la chaine continue toujours, jamais de 429")
    void nonLimitedPath_alwaysPasses() throws Exception {
        request.setServletPath("/api/clients");
        request.setRequestURI("/api/clients");
        request.setRemoteAddr("192.168.1.10");

        for (int i = 0; i < 10; i++) {
            // Reset response entre chaque appel
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
        }

        assertEquals(10, chain.count(), "La chaine doit avoir continue 10 fois");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    @DisplayName("/auth/login : les 2 premieres req passent, la 3eme est 429")
    void authLogin_thirdRequestRateLimited() throws Exception {
        request.setRequestURI("/auth/login");
        request.setRemoteAddr("203.0.113.5");

        // 1ere req : OK
        filter.doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), "1ere req doit passer (200)");

        // 2eme req : OK
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), "2eme req doit passer (200)");

        // 3eme req : 429
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(429, response.getStatus(),
                "3eme req doit etre 429");
        assertTrue(response.getContentAsString().contains("error.rateLimit.exceeded"),
                "Le body doit porter la cle i18n");

        assertEquals(2, chain.count(), "Seules les 2 premieres req traversent la chaine");
    }

    @Test
    @DisplayName("Deux IP distinctes ont des quotas independants")
    void perIpIsolation() throws Exception {
        // IP 1 : sature
        request.setRequestURI("/auth/login");
        request.setRemoteAddr("203.0.113.10");
        filter.doFilter(request, response, chain);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain); // saturee, 429
        assertEquals(429, response.getStatus());

        // IP 2 : nouvelle, doit passer
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/auth/login");
        req2.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        CountingChain chain2 = new CountingChain();
        filter.doFilter(req2, resp2, chain2);
        assertEquals(HttpServletResponse.SC_OK, resp2.getStatus(),
                "IP 2 doit avoir son propre quota");
        assertEquals(1, chain2.count());
    }

    @Test
    @DisplayName("X-Forwarded-For est respecte pour identifier l'IP source derriere proxy")
    void xForwardedFor_isUsed() throws Exception {
        request.setRequestURI("/auth/login");
        request.setRemoteAddr("10.0.0.1"); // adresse du proxy interne
        request.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.1");

        filter.doFilter(request, response, chain);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(429, response.getStatus());

        // Une autre requete avec le meme proxy mais X-Forwarded-For different
        // doit pouvoir passer.
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRequestURI("/auth/login");
        req2.setRemoteAddr("10.0.0.1");
        req2.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        CountingChain chain2 = new CountingChain();
        filter.doFilter(req2, resp2, chain2);
        assertEquals(HttpServletResponse.SC_OK, resp2.getStatus(),
                "IP source differente via XFF doit avoir son propre quota");
    }

    /** Petite chaine de test qui compte les passages. */
    private static final class CountingChain implements FilterChain {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }
    }
}
