package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (Tour 27) du {@link PersonnelController}.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : ADMIN cree un personnel -&gt; 201, pas de hotelId expose, actif=true.</li>
 *   <li>T2 : MENAGE tente POST /api/menage/personnel -&gt; 403 (CRUD personnel
 *       reserve ADMIN/GERANT).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PersonnelControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdminMr;
    private DBUser userMenageMr;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));

        userAdminMr = userRepository.saveAndFlush(buildUser(
                "admin1", "admin1@mr.test", "Sidi", "Admin", mr, admin));
        userMenageMr = userRepository.saveAndFlush(buildUser(
                "menage1", "menage1@mr.test", "Aly", "Menage", mr, menage));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    private static DBUser buildUser(String username, String email, String prenom,
                                    String nom, Hotel hotel, Role role) {
        DBUser user = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    @Test
    @DisplayName("T1 - ADMIN cree un personnel : 201, pas de hotelId expose, actif=true")
    void shouldCreatePersonnelWhenAdmin() throws Exception {
        String jwt = jwtFor(userAdminMr);
        String body = "{"
                + "\"numeroEmploye\":\"MEN-IT-001\","
                + "\"prenom\":\"Khadija\","
                + "\"nom\":\"Mint Sidi\","
                + "\"telephone\":\"+22245111222\","
                + "\"email\":\"khadija@hotel.test\""
                + "}";

        mockMvc.perform(post("/api/menage/personnel")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personnelId").exists())
                .andExpect(jsonPath("$.numeroEmploye").value("MEN-IT-001"))
                .andExpect(jsonPath("$.prenom").value("Khadija"))
                .andExpect(jsonPath("$.nom").value("Mint Sidi"))
                .andExpect(jsonPath("$.nomComplet").value("Khadija Mint Sidi"))
                .andExpect(jsonPath("$.actif").value(true))
                // Pas de hotelId dans le DTO
                .andExpect(jsonPath("$.hotelId").doesNotExist());
    }

    @Test
    @DisplayName("T2 - MENAGE tente POST /api/menage/personnel : 403 (CRUD reserve ADMIN/GERANT)")
    void shouldDenyAccessForMenage() throws Exception {
        String jwt = jwtFor(userMenageMr);
        String body = "{"
                + "\"numeroEmploye\":\"MEN-IT-002\","
                + "\"prenom\":\"X\","
                + "\"nom\":\"Y\""
                + "}";

        mockMvc.perform(post("/api/menage/personnel")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
