package com.cityprojects.citybackend.controller.reporting;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe (*IT) du {@link HebergementReportController} (Tour 41 P1).
 *
 * <p>Couverture : R-HEB-002 ALOS, R-HEB-003 No-show, R-HEB-004 Sources,
 * R-HEB-005 KPI reception. Verifie l'autorisation (RECEPTION 200, MAGASIN 403)
 * + validation Spring 400 (date manquante).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class HebergementReportControllerIT {

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
    private DBUser userReception;
    private DBUser userMagasin;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userReception = userRepository.saveAndFlush(buildUser("rec_heb", "rec@heb.test",
                "Aicha", "Bint", mr, reception));
        userMagasin = userRepository.saveAndFlush(buildUser("mag_heb", "mag@heb.test",
                "Sidi", "Cheikh", mr, magasin));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private String jwtFor(DBUser user) {
        UserPrincipal p = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(p);
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
    @DisplayName("R-HEB-002 - RECEPTION GET /alos : 200")
    void shouldGetAlos() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/hebergement/alos")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-HEB-002 - MAGASIN GET /alos : 403")
    void shouldDenyAlosMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/hebergement/alos")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-HEB-003 - RECEPTION GET /no-show-rate : 200")
    void shouldGetNoShow() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/hebergement/no-show-rate")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-HEB-004 - RECEPTION GET /sources : 200")
    void shouldGetSources() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/hebergement/sources")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-HEB-005 - RECEPTION GET /kpi-reception : 200")
    void shouldGetKpiReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/hebergement/kpi-reception")
                        .param("date", "2026-05-14")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-HEB-005 - GET /kpi-reception sans date : 400")
    void shouldRejectMissingDate() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/hebergement/kpi-reception")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest());
    }
}
