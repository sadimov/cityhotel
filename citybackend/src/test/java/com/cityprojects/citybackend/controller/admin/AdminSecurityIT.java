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
 * Tests Failsafe : matrice de securite globale du module admin.
 *
 * <p>Verifie que :
 * <ul>
 *   <li>Un user RECEPTION (non-SUPERADMIN) tape sur {@code /api/admin/hotels}
 *       et recoit 403 (PreAuthorize bloque).</li>
 *   <li>Un user SUPERADMIN tape sur {@code /api/admin/hotels} et recoit 200.</li>
 * </ul>
 *
 * <p>Garantit que la regle "tout endpoint admin = SUPERADMIN uniquement" est
 * effective au niveau de la chaine HTTP (pas seulement annote dans le code).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminSecurityIT {

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
    private DBUser receptionUser;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.parametres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel hotel = new Hotel("ADM1", "Hotel Admin Test");
        hotel.setCodePays("MR");
        hotelRepository.saveAndFlush(hotel);

        Role superadminRole = roleRepository.saveAndFlush(new Role("SUPERADMIN", "SuperAdmin"));
        Role receptionRole = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));

        superAdminUser = userRepository.saveAndFlush(buildUser(
                "superadm", "superadm@test", "Super", "Admin", hotel, superadminRole));
        receptionUser = userRepository.saveAndFlush(buildUser(
                "reception", "rec@test", "Recep", "Tion", hotel, receptionRole));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.parametres");
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
        DBUser u = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                prenom, nom, hotel, role);
        u.setActif(Boolean.TRUE);
        u.setCompteVerrouille(Boolean.FALSE);
        return u;
    }

    @Test
    @DisplayName("T1 - RECEPTION sur /api/admin/hotels -> 403 (PreAuthorize hasRole(SUPERADMIN) bloque)")
    void shouldDenyReceptionOnAdminEndpoint() throws Exception {
        String jwt = jwtFor(receptionUser);

        mockMvc.perform(get("/api/admin/hotels")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T2 - SUPERADMIN sur /api/admin/hotels -> 200")
    void shouldAllowSuperadminOnAdminEndpoint() throws Exception {
        String jwt = jwtFor(superAdminUser);

        mockMvc.perform(get("/api/admin/hotels")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}
