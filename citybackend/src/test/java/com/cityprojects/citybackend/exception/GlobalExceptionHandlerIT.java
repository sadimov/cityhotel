package com.cityprojects.citybackend.exception;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe : couverture des handlers ajoutes au GlobalExceptionHandler
 * (Tour 38 C9).
 *
 * <p>Verifie que les cles i18n et codes HTTP attendus sont retournes pour
 * body malforme, methode HTTP non supportee, et bad credentials.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class GlobalExceptionHandlerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser superAdminUser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.refresh_tokens");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel hotel = new Hotel("EXC", "Hotel Exception Test");
        hotel.setCodePays("MR");
        hotelRepository.saveAndFlush(hotel);

        Role role = roleRepository.saveAndFlush(new Role("SUPERADMIN", "SuperAdmin"));
        superAdminUser = new DBUser("supe-exc", "supe@exc.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Su", "Exc", hotel, role);
        superAdminUser.setActif(Boolean.TRUE);
        superAdminUser.setCompteVerrouille(Boolean.FALSE);
        userRepository.saveAndFlush(superAdminUser);

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

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    @Test
    @DisplayName("Body JSON malforme sur /auth/login : 400 + cle error.body.malformed")
    void malformedJson_returnsBodyMalformedKey() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("error.body.malformed"));
    }

    @Test
    @DisplayName("DELETE sur /auth/login (POST attendu) : 405 + cle error.method.notAllowed")
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(delete("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("error.method.notAllowed"));
    }

    @Test
    @DisplayName("Bad credentials login : reponse erreur (le controller AuthController catche encore Exception — Tour suivant)")
    void badCredentials_returnsErrorResponse() throws Exception {
        // Note : actuellement AuthController.login() catche Exception et renvoie 400
        // avec le message brut (cf. ligne 56-60). Ce comportement doit etre nettoye
        // dans un Tour suivant pour beneficier pleinement du GlobalExceptionHandler.
        // Pour l'heure on verifie juste qu'on a bien une reponse erreur.
        Map<String, Object> body = Map.of("username", "doesnotexist-user", "password", "wrongPass99");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
