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

@SpringBootTest
@ActiveProfiles("test")
class InventoryReportControllerIT {

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
    private DBUser userMagasin;
    private DBUser userReception;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));
        userMagasin = userRepository.saveAndFlush(build("mag_inv", "mag@inv.test", mr, magasin));
        userReception = userRepository.saveAndFlush(build("rec_inv", "rec@inv.test", mr, reception));
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
    @DisplayName("R-INV-002 - MAGASIN GET /mouvements-valorises : 200")
    void shouldGetMouvements() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/inventory/mouvements-valorises")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-INV-002 - RECEPTION GET : 403")
    void shouldDenyReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/inventory/mouvements-valorises")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-INV-003 - MAGASIN GET /bc-pendants : 200")
    void shouldGetBcPendants() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/inventory/bc-pendants")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-INV-003 - MAGASIN GET /rotation-produits : 200")
    void shouldGetRotation() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/inventory/rotation-produits")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}
