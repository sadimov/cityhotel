package com.cityprojects.citybackend.controller.hebergement;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe : valide la chaine JWT &rarr; {@code @PreAuthorize}
 * &rarr; {@code @TenantId} &rarr; {@code NightAuditService} (Tour 13).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : POST /api/hebergement/night-audit/run avec JWT ADMIN -&gt; 200 + {@code NightAuditResultDto}.</li>
 *   <li>T2 : POST /api/hebergement/night-audit/run avec JWT MAGASIN -&gt; 403 (matrice
 *       {@code @PreAuthorize} : MAGASIN n'est pas autorise).</li>
 * </ol>
 *
 * <p><b>Note T3 cross-tenant</b> : differe car le JWT porte deja un {@code hotelId}
 * cote serveur ; le scenario "sans tenant" est bloque en amont par la regle
 * d'authentification (Tour 7B C1) qui refuse 401 si pas de hotel sur le user.
 * L'isolation cross-tenant est deja couverte par {@code NightAuditServiceTests T5}.</p>
 *
 * <p>Le profil "test" desactive le scheduler ({@code city.scheduler.enabled=false})
 * pour que les crons {@code @Scheduled} ne s'auto-declenchent pas pendant les tests.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class NightAuditControllerIT {

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
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdminMr;
    private DBUser userMagasinMr;
    private Long hotelMrId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userAdminMr = userRepository.saveAndFlush(buildUser(
                "admin1", "admin1@mr.test", "Aicha", "Bint", mr, admin));
        userMagasinMr = userRepository.saveAndFlush(buildUser(
                "magasin1", "magasin1@mr.test", "Sidi", "Cheikh", mr, magasin));

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
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
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
    @DisplayName("T1 - ADMIN hotel MR declenche le night audit : 200 + DTO")
    void shouldRunNightAuditWhenAdmin() throws Exception {
        String jwt = jwtFor(userAdminMr);

        // Aucune reservation creee : compteurs a 0 mais 200 OK avec DTO valide.
        mockMvc.perform(post("/api/hebergement/night-audit/run")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(hotelMrId.intValue()))
                .andExpect(jsonPath("$.dateExecution").exists())
                .andExpect(jsonPath("$.nbReservationsMarkedNoShow").value(0))
                .andExpect(jsonPath("$.nbNuiteesManquantesGenerees").value(0))
                .andExpect(jsonPath("$.executedAt").exists());
    }

    @Test
    @DisplayName("T2 - MAGASIN tente POST /api/hebergement/night-audit/run : 403")
    void shouldDenyAccessForMagasin() throws Exception {
        String jwt = jwtFor(userMagasinMr);

        mockMvc.perform(post("/api/hebergement/night-audit/run")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }
}
