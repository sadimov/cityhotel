package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Parametre;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.ParametreRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe : controller {@code /api/admin/parametres} (Tour 31).
 *
 * <p>Couverture :
 * <ul>
 *   <li>T1 : POST creation parametre -&gt; 201, modifiable=true force.</li>
 *   <li>T2 : PUT sur parametre {@code modifiable=false} -&gt; 4xx avec message
 *       {@code error.parametre.notModifiable}.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ParametreAdminControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ParametreRepository parametreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser superAdminUser;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.parametres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel anchor = new Hotel("ANCH", "Hotel ancrage SA");
        anchor.setCodePays("MR");
        hotelRepository.saveAndFlush(anchor);
        Role superadminRole = roleRepository.saveAndFlush(new Role("SUPERADMIN", "SuperAdmin"));

        superAdminUser = userRepository.saveAndFlush(buildUser(
                "superadm", "sa@test", "Sup", "Adm", anchor, superadminRole));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.parametres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private String jwt() {
        UserPrincipal principal = UserPrincipal.create(superAdminUser, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    private static DBUser buildUser(String username, String email, String prenom,
                                    String nom, Hotel hotel, Role role) {
        DBUser u = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        u.setActif(Boolean.TRUE);
        u.setCompteVerrouille(Boolean.FALSE);
        return u;
    }

    @Test
    @DisplayName("T1 - POST /api/admin/parametres -> 201, modifiable=true force")
    void shouldCreateParametreWithModifiableTrue() throws Exception {
        String body = "{"
                + "\"cle\":\"notification.sms.enabled\","
                + "\"valeur\":\"false\","
                + "\"description\":\"Activation SMS\","
                + "\"categorie\":\"notification\""
                + "}";

        mockMvc.perform(post("/api/admin/parametres")
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cle").value("notification.sms.enabled"))
                .andExpect(jsonPath("$.data.modifiable").value(true));
    }

    @Test
    @DisplayName("T2 - PUT sur parametre modifiable=false -> 4xx + error.parametre.notModifiable")
    void shouldRefuseUpdateOnNonModifiable() throws Exception {
        Parametre systeme = new Parametre();
        systeme.setCle("app.timezone");
        systeme.setValeur("Africa/Nouakchott");
        systeme.setModifiable(Boolean.FALSE);
        systeme.setCategorie("system");
        Long id = parametreRepository.saveAndFlush(systeme).getParametreId();

        String body = "{\"valeur\":\"UTC\"}";

        mockMvc.perform(put("/api/admin/parametres/" + id)
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("error.parametre.notModifiable"));
    }
}
