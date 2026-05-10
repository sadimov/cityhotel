package com.cityprojects.citybackend.controller.auth;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe : rate limit sur /auth/login (Tour 38 C10).
 *
 * <p>Force la config du rate limiter en re-injectant un {@link RateLimiter}
 * avec un quota tres bas (3 req/min) pour declencher le 429 sans saturer le
 * quota par defaut (10/min) qui ralentirait inutilement.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRateLimitIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.refresh_tokens");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel hotel = new Hotel("RLT", "Hotel Rate Limit");
        hotel.setCodePays("MR");
        hotelRepository.saveAndFlush(hotel);
        roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));

        // Reset le cache de RateLimiter par IP du filtre + injecte un quota bas
        // (3 req/min) pour cet IT.
        Field field = RateLimitFilter.class.getDeclaredField("limitersByIp");
        field.setAccessible(true);
        ConcurrentHashMap<String, RateLimiter> limitersByIp =
                (ConcurrentHashMap<String, RateLimiter>) field.get(rateLimitFilter);
        limitersByIp.clear();

        // Pre-charge un RateLimiter restrictif pour l'IP locale 127.0.0.1
        // (utilisee par MockMvc).
        RateLimiterConfig restrictive = RateLimiterConfig.custom()
                .limitForPeriod(3)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        limitersByIp.put("127.0.0.1",
                RateLimiterRegistry.of(restrictive).rateLimiter("auth-127.0.0.1"));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() throws Exception {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.refresh_tokens");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Field field = RateLimitFilter.class.getDeclaredField("limitersByIp");
        field.setAccessible(true);
        ((ConcurrentHashMap<String, RateLimiter>) field.get(rateLimitFilter)).clear();
    }

    @Test
    @DisplayName("Apres 3 tentatives infructueuses : 4eme retourne 429")
    void fourthAttempt_returns429() throws Exception {
        Map<String, Object> bad = Map.of("username", "doesnotexist", "password", "wrongpass99");
        String body = objectMapper.writeValueAsString(bad);

        // 3 premieres req : reponse erreur du controller (badrequest 400 actuellement —
        // AuthController catche Exception, nettoyage prevu Tour suivant). Important :
        // pas de 429.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // 4eme req : 429 par le RateLimitFilter (court-circuite le controller).
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("error.rateLimit.exceeded"));
    }
}
