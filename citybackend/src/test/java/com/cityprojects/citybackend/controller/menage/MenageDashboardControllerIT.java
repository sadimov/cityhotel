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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (sous-tour F2) du
 * {@link MenageDashboardController}.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : ADMIN GET /api/menage/dashboard -&gt; 200, structure
 *       complete (statistiques + tachesEnRetard[] + personnelsDisponibles[]).</li>
 *   <li>T2 : RECEPTION GET /api/menage/statistiques -&gt; 200 (autorise
 *       par spec).</li>
 *   <li>T3 : ADMIN GET /api/menage/statistiques/periode?dateDebut&dateFin
 *       -&gt; 200, dateReference = dateDebut.</li>
 *   <li>T4 : RECEPTION GET /api/menage/kpi -&gt; 200.</li>
 *   <li>T5 : MENAGE GET /api/menage/dashboard -&gt; 403 (role MENAGE non
 *       autorise sur le dashboard — reserve ADMIN/GERANT/RECEPTION).</li>
 *   <li>T6 : RECEPTION GET /api/menage/statistiques/personnel/{id} -&gt; 403
 *       (donnee RH plus sensible, reservee ADMIN/GERANT).</li>
 * </ol>
 *
 * <p>Setup minimal : BDD vide pour chaque test ; les counts retournent 0
 * partout, taux de realisation = 0, tempsRealisationMoyen = null. C'est
 * suffisant pour valider la structure du JSON et la matrice de roles.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MenageDashboardControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdmin;
    private DBUser userReception;
    private DBUser userMenage;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));

        userAdmin = userRepository.saveAndFlush(buildUser(
                "admin-dash", "admin-dash@mr.test", "Sidi", "Admin", mr, admin));
        userReception = userRepository.saveAndFlush(buildUser(
                "reception-dash", "reception-dash@mr.test", "Aly", "Reception", mr, reception));
        userMenage = userRepository.saveAndFlush(buildUser(
                "menage-dash", "menage-dash@mr.test", "Salem", "Menage", mr, menage));

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
    @DisplayName("T1 - ADMIN GET /dashboard : 200, structure complete")
    void shouldReturnDashboardWhenAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);

        mockMvc.perform(get("/api/menage/dashboard")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                // Note IT : les controllers sont enveloppes par
                // ApiResponseBodyAdvice -> payload accessible via $.data.*
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statistiques").exists())
                .andExpect(jsonPath("$.data.statistiques.nombrePersonnelActif").exists())
                .andExpect(jsonPath("$.data.tachesEnRetard").isArray())
                .andExpect(jsonPath("$.data.personnelsDisponibles").isArray());
    }

    @Test
    @DisplayName("T2 - RECEPTION GET /statistiques : 200")
    void shouldReturnStatistiquesWhenReception() throws Exception {
        String jwt = jwtFor(userReception);

        mockMvc.perform(get("/api/menage/statistiques")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateReference").exists())
                .andExpect(jsonPath("$.data.nombreTachesAujourdhui").exists())
                .andExpect(jsonPath("$.data.repartitionParStatut").exists())
                .andExpect(jsonPath("$.data.repartitionParType").exists())
                .andExpect(jsonPath("$.data.repartitionParPriorite").exists());
    }

    @Test
    @DisplayName("T3 - ADMIN GET /statistiques/periode : 200, dateReference = dateDebut")
    void shouldReturnStatistiquesPeriode() throws Exception {
        String jwt = jwtFor(userAdmin);

        mockMvc.perform(get("/api/menage/statistiques/periode")
                        .param("dateDebut", "2026-05-01")
                        .param("dateFin", "2026-05-14")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateReference").value("2026-05-01"));
    }

    @Test
    @DisplayName("T4 - RECEPTION GET /kpi : 200")
    void shouldReturnKpiWhenReception() throws Exception {
        String jwt = jwtFor(userReception);

        mockMvc.perform(get("/api/menage/kpi")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateReference").exists())
                .andExpect(jsonPath("$.data.nombrePersonnelActif").exists())
                .andExpect(jsonPath("$.data.tauxRealisationJour").exists());
    }

    @Test
    @DisplayName("T5 - MENAGE GET /dashboard : 403 (role non autorise)")
    void shouldDenyDashboardForMenage() throws Exception {
        String jwt = jwtFor(userMenage);

        mockMvc.perform(get("/api/menage/dashboard")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T6 - RECEPTION GET /statistiques/personnel/{id} : 403 (RH-sensible)")
    void shouldDenyPerformancePersonnelForReception() throws Exception {
        String jwt = jwtFor(userReception);

        mockMvc.perform(get("/api/menage/statistiques/personnel/1")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }
}
