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
 * Tests Failsafe (*IT) du {@link FinanceReportController} (Tour 41 P1/P2).
 */
@SpringBootTest
@ActiveProfiles("test")
class FinanceReportControllerIT {

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
    private DBUser userAdmin;
    private DBUser userReception;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);
        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        userAdmin = userRepository.saveAndFlush(build("adm_fin", "adm@fin.test", mr, admin));
        userReception = userRepository.saveAndFlush(build("rec_fin", "rec@fin.test", mr, reception));
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

    private static DBUser build(String username, String email, Hotel hotel, Role role) {
        DBUser u = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "First", "Last", hotel, role);
        u.setActif(Boolean.TRUE);
        u.setCompteVerrouille(Boolean.FALSE);
        return u;
    }

    @Test
    @DisplayName("R-FIN-002 - ADMIN GET /encours-clients : 200")
    void shouldGetEncours() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/finance/encours-clients")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-FIN-002 - RECEPTION GET /encours-clients : 403")
    void shouldDenyEncoursReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/finance/encours-clients")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-FIN-003 - ADMIN GET /tva-recap : 200")
    void shouldGetTvaRecap() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/finance/tva-recap")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-FIN-004 - ADMIN GET /top-societes : 200")
    void shouldGetTopSocietes() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/finance/top-societes")
                        .param("from", "2026-01-01").param("to", "2027-01-01").param("limit", "10")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}
