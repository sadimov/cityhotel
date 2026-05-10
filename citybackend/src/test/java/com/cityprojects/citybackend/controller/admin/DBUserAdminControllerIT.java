package com.cityprojects.citybackend.controller.admin;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Failsafe : controller {@code /api/admin/hotels/{hotelId}/users} (Tour 31).
 *
 * <p>Couverture :
 * <ul>
 *   <li>T1 : POST creation user dans un hotel donne -&gt; 201.</li>
 *   <li>T2 : GET cross-hotel ({@code /hotels/<autre>/users/<userId-de-MR>}) -&gt; 404.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class DBUserAdminControllerIT {

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
    private DBUser superAdminUser;
    private Long hotelMrId;
    private Long hotelFrId;
    private Integer roleGerantId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel MR");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel FR");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role superadminRole = roleRepository.saveAndFlush(new Role("SUPERADMIN", "SuperAdmin"));
        roleGerantId = roleRepository.saveAndFlush(new Role("GERANT", "Gerant")).getRoleId();

        superAdminUser = userRepository.saveAndFlush(buildUser(
                "superadm", "sa@test", "Sup", "Adm", mr, superadminRole));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
    @DisplayName("T1 - POST /api/admin/hotels/{hotelId}/users (SUPERADMIN) -> 201")
    void shouldCreateUserForHotel() throws Exception {
        String body = "{"
                + "\"username\":\"newuser\","
                + "\"email\":\"newuser@mr.test\","
                + "\"password\":\"Pwd123456!\","
                + "\"prenom\":\"New\","
                + "\"nom\":\"User\","
                + "\"roleId\":" + roleGerantId
                + "}";

        mockMvc.perform(post("/api/admin/hotels/" + hotelMrId + "/users")
                        .header("Authorization", "Bearer " + jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.hotelId").value(hotelMrId));
    }

    @Test
    @DisplayName("T2 - GET cross-hotel /hotels/{FR}/users/{userId-de-MR} -> 404 (anti-leak)")
    void shouldReturn404OnCrossHotelGet() throws Exception {
        // Cree un user dans MR (via repo direct pour eviter les dependances HTTP)
        Role gerant = roleRepository.findById(roleGerantId).orElseThrow();
        Hotel mr = hotelRepository.findById(hotelMrId).orElseThrow();
        DBUser userInMr = userRepository.saveAndFlush(buildUser(
                "umrtest", "umr@test", "Use", "MaR", mr, gerant));

        // Tente de le lire via la route hotelFrId
        mockMvc.perform(get("/api/admin/hotels/" + hotelFrId + "/users/" + userInMr.getUserId())
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isNotFound());
    }
}
