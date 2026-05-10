package com.cityprojects.citybackend.controller.auth;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.RefreshToken;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RefreshTokenRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.service.auth.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe : flow login -> refresh -> rotation + detection de reutilisation
 * (Tour 38 C6/C7).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRefreshRotationIT {

    private static final String USERNAME = "rotation-tester";
    private static final String PASSWORD = "RotationTest123!";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser user;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.refresh_tokens");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel hotel = new Hotel("HOTRE", "Hotel Rotation Test");
        hotel.setCodePays("MR");
        hotelRepository.saveAndFlush(hotel);

        Role role = roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));

        user = new DBUser(USERNAME, "rotation@test.local",
                passwordEncoder.encode(PASSWORD), "Rota", "Tion", hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userRepository.saveAndFlush(user);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.refresh_tokens");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    @Test
    @DisplayName("issue() persiste le token avec hash != clair, expiration calculee")
    void issue_persistsHashAndExpiration() {
        // Note : on n'utilise PAS /auth/login en IT car la creation de UserSession
        // exige une table user_sessions avec un type 'inet' non supporte par H2.
        // Le test unitaire RefreshTokenServiceTests couvre deja ce cas via mocks ;
        // ici on verifie le contrat sur la VRAIE table H2 (full Spring Boot).
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(
                user.getUserId(), user.getHotel().getHotelId(), "JUnit/IT", "127.0.0.1");

        assertNotNull(issued.clearToken());
        assertNotEquals(issued.clearToken(), issued.entity().getTokenHash(),
                "hash != clear token");

        List<RefreshToken> stored = refreshTokenRepository.findByUserIdAndRevokedFalse(user.getUserId());
        assertEquals(1, stored.size(), "Un refresh token doit etre persiste");
        assertFalse(stored.get(0).getTokenHash().isBlank());
        assertNotNull(stored.get(0).getExpiresAt(), "expiresAt doit etre populated");
    }

    @Test
    @DisplayName("Refresh : token rotate (ancien revoked, nouveau emis)")
    void refresh_rotatesToken() throws Exception {
        // 1. Issue un refresh via le service directement (plus rapide que login)
        RefreshTokenService.IssuedToken first =
                refreshTokenService.issue(user.getUserId(), user.getHotel().getHotelId(), null, null);

        // 2. Refresh via API
        Map<String, String> body = Map.of("token", first.clearToken());
        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        // 3. Le clair retourne doit etre different
        String json = result.getResponse().getContentAsString();
        Map<?, ?> resp = objectMapper.readValue(json, Map.class);
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        String newToken = (String) data.get("refreshToken");
        assertNotNull(newToken);
        assertNotEquals(first.clearToken(), newToken, "Le refresh token doit avoir change");

        // 4. L'ancien token est revoked en BDD
        RefreshToken refreshed = refreshTokenRepository.findById(first.entity().getId())
                .orElseThrow();
        assertTrue(Boolean.TRUE.equals(refreshed.getRevoked()),
                "Ancien refresh token doit etre revoked");
        assertNotNull(refreshed.getReplacedById(), "ReplacedById doit pointer le nouveau");
    }

    @Test
    @DisplayName("Reutilisation d'un refresh deja revoked : detection vol -> revoque tous les tokens du user")
    void refresh_reusedToken_revokesAllUserTokens() throws Exception {
        // 1. Issue 2 refresh tokens distincts.
        RefreshTokenService.IssuedToken first = refreshTokenService.issue(user.getUserId(),
                user.getHotel().getHotelId(), null, null);
        RefreshTokenService.IssuedToken second = refreshTokenService.issue(user.getUserId(),
                user.getHotel().getHotelId(), null, null);

        // 2. Rotate le premier => devient revoked.
        refreshTokenService.rotate(first.clearToken(), null, null);

        // 3. Re-tenter le premier => detection de reutilisation.
        // Note : AuthController.refreshToken() catche Exception et renvoie 400 actuellement
        // (le nettoyage de ce catch est differe a un Tour suivant). On verifie donc juste
        // qu'on a une reponse erreur, et surtout l'effet metier (revocation cross-device)
        // a l'etape 4.
        Map<String, String> body = Map.of("token", first.clearToken());
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        // 4. TOUS les refresh tokens du user doivent etre revoked (cross-device).
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedFalse(user.getUserId());
        assertTrue(active.isEmpty(),
                "Aucun refresh token actif ne doit subsister apres detection de vol");

        // Le 2eme token (jamais utilise) doit aussi etre revoked.
        RefreshToken stored2 = refreshTokenRepository.findById(second.entity().getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(stored2.getRevoked()),
                "2eme token (jamais utilise) doit aussi etre revoked apres detection de vol");
    }
}
