package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.Planning;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.PlanningRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (sous-tour F3) du nouvel endpoint
 * {@code GET /api/menage/personnel/disponibles?date=...}.
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 : 3 agents — A planifie dispo / B planifie indispo / C sans
 *       planning. ADMIN GET /disponibles?date=today renvoie [A] uniquement.</li>
 *   <li>T2 : agent inactif planifie dispo n'apparait pas (filtre actif=true).</li>
 *   <li>T3 : MENAGE GET /disponibles : 200 (role autorise).</li>
 *   <li>T4 : pas de param date -&gt; defaut today (test sans param,
 *       structure de la reponse seulement).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PersonnelDisponiblesIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private PlanningRepository planningRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdmin;
    private DBUser userMenage;
    private Long hotelId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);
        hotelId = mr.getHotelId();

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));

        userAdmin = userRepository.saveAndFlush(buildUser(
                "admin-disp", "admin-disp@mr.test", "Sidi", "Admin", mr, admin));
        userMenage = userRepository.saveAndFlush(buildUser(
                "menage-disp", "menage-disp@mr.test", "Salem", "Menage", mr, menage));

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

    /**
     * Creation d'un personnel via JdbcTemplate (bypass @TenantId Hibernate
     * pour le seed). Force directement hotel_id pour eviter d'avoir a poser
     * TenantContext avant le save() applicatif.
     */
    private Long seedPersonnel(String numero, String prenom, String nom, boolean actif) {
        TenantContext.set(hotelId);
        try {
            Personnel p = new Personnel();
            p.setNumeroEmploye(numero);
            p.setPrenom(prenom);
            p.setNom(nom);
            p.setActif(actif);
            return personnelRepository.saveAndFlush(p).getPersonnelId();
        } finally {
            TenantContext.clear();
        }
    }

    private void seedPlanning(Long personnelId, LocalDate date, boolean disponible) {
        TenantContext.set(hotelId);
        try {
            Planning pl = new Planning();
            pl.setPersonnelId(personnelId);
            pl.setDateTravail(date);
            pl.setHeureDebut(LocalTime.of(8, 0));
            pl.setHeureFin(LocalTime.of(16, 0));
            pl.setDisponible(disponible);
            planningRepository.saveAndFlush(pl);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - 3 agents A/B/C : seul A (planifie dispo) est renvoye")
    void shouldReturnOnlyPersonnelWithAvailablePlanningForDate() throws Exception {
        LocalDate today = LocalDate.now();
        Long idA = seedPersonnel("MEN-A", "Ahmed", "Hassan", true);
        Long idB = seedPersonnel("MEN-B", "Mariam", "Diallo", true);
        Long idC = seedPersonnel("MEN-C", "Khalid", "Ould", true);
        seedPlanning(idA, today, true);   // dispo aujourd'hui
        seedPlanning(idB, today, false);  // bloque (conge)
        // C : aucun planning

        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/menage/personnel/disponibles")
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                // Reponse enveloppee par ApiResponseBodyAdvice -> $.data.*
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].personnelId").value(idA))
                .andExpect(jsonPath("$.data[0].numeroEmploye").value("MEN-A"))
                .andExpect(jsonPath("$.data[0].nomComplet").value("Ahmed Hassan"));
    }

    @Test
    @DisplayName("T2 - agent inactif planifie dispo n'apparait pas")
    void shouldExcludeInactivePersonnelEvenIfPlanningAvailable() throws Exception {
        LocalDate today = LocalDate.now();
        Long idInactif = seedPersonnel("MEN-X", "Inactif", "User", false);
        seedPlanning(idInactif, today, true);

        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/menage/personnel/disponibles")
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("T3 - role MENAGE autorise sur /disponibles")
    void shouldReturn200ForMenage() throws Exception {
        String jwt = jwtFor(userMenage);
        mockMvc.perform(get("/api/menage/personnel/disponibles")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T4 - pas de param date -> default today, reponse JSON array")
    void shouldDefaultToTodayWhenNoDateParam() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/menage/personnel/disponibles")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
