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
class RestaurantReportControllerIT {

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
    private DBUser userRestaurant;
    private DBUser userMenage;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();
        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);
        Role restaurant = roleRepository.saveAndFlush(new Role("RESTAURANT", "Restaurant"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));
        userRestaurant = userRepository.saveAndFlush(build("res_user", "res@res.test", mr, restaurant));
        userMenage = userRepository.saveAndFlush(build("men_user", "men@res.test", mr, menage));
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
    @DisplayName("R-RES-001 - RESTAURANT GET /journal-caisse : 200")
    void shouldGetJournal() throws Exception {
        String jwt = jwtFor(userRestaurant);
        mockMvc.perform(get("/api/reports/restaurant/journal-caisse")
                        .param("date", "2026-05-14")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-RES-001 - MENAGE GET /journal-caisse : 403")
    void shouldDenyMenage() throws Exception {
        String jwt = jwtFor(userMenage);
        mockMvc.perform(get("/api/reports/restaurant/journal-caisse")
                        .param("date", "2026-05-14")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-RES-002 - RESTAURANT GET /top-articles : 200")
    void shouldGetTopArticles() throws Exception {
        String jwt = jwtFor(userRestaurant);
        mockMvc.perform(get("/api/reports/restaurant/top-articles")
                        .param("from", "2026-01-01").param("to", "2026-02-01").param("limit", "10")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-RES-003 - RESTAURANT GET /ticket-moyen : 200")
    void shouldGetTicketMoyen() throws Exception {
        String jwt = jwtFor(userRestaurant);
        mockMvc.perform(get("/api/reports/restaurant/ticket-moyen")
                        .param("from", "2026-01-01").param("to", "2026-02-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}
