package com.cityprojects.citybackend.controller.finance;

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

import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe du {@link TauxTvaConfigController} B4.
 *
 * <p>Cas couverts :</p>
 * <ol>
 *   <li>T1 - GERANT lit /api/finance/tva/config -&gt; 200, contient tous les
 *       types (defauts codes + persistes).</li>
 *   <li>T2 - GERANT tente PUT -&gt; 403 (reserve SUPERADMIN/ADMIN).</li>
 *   <li>T3 - ADMIN PUT taux 16% sur HEBERGEMENT_NUITEE -&gt; 200, valeur prise
 *       en compte au lookup suivant.</li>
 *   <li>T4 - RECEPTION GET -&gt; 403 (pas dans la matrice).</li>
 *   <li>T5 - non authentifie -&gt; 401.</li>
 *   <li>T6 - PUT taux invalide (100) -&gt; 400.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TauxTvaConfigControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdmin;
    private DBUser userGerant;
    private DBUser userReception;
    private Long hotelId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel TVA Test");
        mr.setCodePays("MR");
        hotelId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));

        userAdmin = userRepository.saveAndFlush(buildUser("adminTva", "admin@h.test",
                "Admin", "Tva", mr, admin));
        userGerant = userRepository.saveAndFlush(buildUser("gerantTva", "g@h.test",
                "Gerant", "Tva", mr, gerant));
        userReception = userRepository.saveAndFlush(buildUser("recepTva", "r@h.test",
                "Recep", "Tva", mr, reception));

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
        jdbcTemplate.update("DELETE FROM finance.taux_tva_config");
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
    @DisplayName("T1 - GERANT GET /tva/config -> 200 + tous les types presents")
    void shouldListAll() throws Exception {
        // Reponses enveloppees {success, message, data, timestamp}
        mockMvc.perform(get("/api/finance/tva/config")
                        .header("Authorization", "Bearer " + jwtFor(userGerant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))  // 7 TypeServiceTva
                .andExpect(jsonPath("$.data[?(@.typeService=='HEBERGEMENT_NUITEE')]").exists())
                .andExpect(jsonPath("$.data[?(@.typeService=='ACHAT_MARCHANDISES')]").exists());
    }

    @Test
    @DisplayName("T2 - GERANT PUT -> 403 (reserve SUPERADMIN/ADMIN)")
    void shouldRefusePutForGerant() throws Exception {
        mockMvc.perform(put("/api/finance/tva/config/RESTAURATION")
                        .header("Authorization", "Bearer " + jwtFor(userGerant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taux\":18.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - ADMIN PUT taux 16% sur HEBERGEMENT_NUITEE -> 200 + valeur persiste")
    void shouldUpdateForAdmin() throws Exception {
        mockMvc.perform(put("/api/finance/tva/config/HEBERGEMENT_NUITEE")
                        .header("Authorization", "Bearer " + jwtFor(userAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taux\":16.00,\"libelle\":\"Hotel touristique\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeService").value("HEBERGEMENT_NUITEE"))
                .andExpect(jsonPath("$.data.taux").value(16.00))
                .andExpect(jsonPath("$.data.defaut").value(false));

        // GET unique -> doit refleter la nouvelle valeur
        mockMvc.perform(get("/api/finance/tva/config/HEBERGEMENT_NUITEE")
                        .header("Authorization", "Bearer " + jwtFor(userAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taux").value(16.00));
    }

    @Test
    @DisplayName("T4 - RECEPTION GET -> 403 (pas dans la matrice)")
    void shouldRefuseReception() throws Exception {
        mockMvc.perform(get("/api/finance/tva/config")
                        .header("Authorization", "Bearer " + jwtFor(userReception)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T5 - non authentifie -> 401")
    void shouldRefuseAnonymous() throws Exception {
        mockMvc.perform(get("/api/finance/tva/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T6 - PUT taux 100 (depasse la borne) -> 400")
    void shouldRejectInvalidTaux() throws Exception {
        mockMvc.perform(put("/api/finance/tva/config/RESTAURATION")
                        .header("Authorization", "Bearer " + jwtFor(userAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taux\":100.00}"))
                .andExpect(status().isBadRequest());
    }
}
