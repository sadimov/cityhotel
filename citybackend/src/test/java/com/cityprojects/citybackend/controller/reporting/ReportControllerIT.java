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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe (*IT) du {@link ReportController} (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ul>
 *   <li>R-HEB-001 : 200 GET JSON pour ADMIN ; 403 pour MAGASIN.</li>
 *   <li>R-FIN-001 : 200 GET JSON pour ADMIN ; 403 pour RECEPTION (non autorise) ;
 *       export XLSX content-type OK.</li>
 *   <li>R-INV-001 : 200 GET JSON pour MAGASIN ; 403 pour RECEPTION ;
 *       export XLSX content-type OK.</li>
 *   <li>R-NA-001 : 200 GET JSON pour ADMIN ; 403 pour MAGASIN ;
 *       validation 400 si from manquant.</li>
 *   <li>R-CLI-001 : 200 GET JSON pour RECEPTION ; export XLSX content-type OK.</li>
 * </ul>
 *
 * <p>Profil "test" : Liquibase desactive (schemas crees a la connexion H2),
 * scheduler desactive, JWT secret factice (>= 64 chars).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportControllerIT {

    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
    private DBUser userMagasin;
    private DBUser userReception;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelRepository.saveAndFlush(mr);

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Administrateur"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        Role reception = roleRepository.saveAndFlush(new Role("RECEPTION", "Reception"));

        userAdmin = userRepository.saveAndFlush(buildUser(
                "admin_rep", "admin@rep.test", "Aicha", "Bint", mr, admin));
        userMagasin = userRepository.saveAndFlush(buildUser(
                "magasin_rep", "magasin@rep.test", "Sidi", "Cheikh", mr, magasin));
        userReception = userRepository.saveAndFlush(buildUser(
                "reception_rep", "reception@rep.test", "Hawa", "Mint", mr, reception));

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
        // Reporting est read-only -> on n'a rien a nettoyer dans des tables reporting.
        // On nettoie uniquement les tables core seedees par setUp().
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

    // ============================================================================
    // R-HEB-001 Occupation
    // ============================================================================

    @Test
    @DisplayName("R-HEB-001 - ADMIN GET /occupation : 200 + DTO JSON")
    void shouldGetOccupationForAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/occupation").param("periode", "JOUR")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalChambres").exists())
                .andExpect(jsonPath("$.data.tauxOccupationGlobal").exists())
                .andExpect(jsonPath("$.data.breakdownParType").isArray());
    }

    @Test
    @DisplayName("R-HEB-001 - MAGASIN GET /occupation : 403")
    void shouldDenyOccupationForMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/occupation").param("periode", "JOUR")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // R-FIN-001 CA
    // ============================================================================

    @Test
    @DisplayName("R-FIN-001 - ADMIN GET /ca : 200 + DTO JSON MRU")
    void shouldGetCaForAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/ca").param("periode", "SEMAINE")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devise").value("MRU"))
                .andExpect(jsonPath("$.data.nbFactures").value(0));
    }

    @Test
    @DisplayName("R-FIN-001 - RECEPTION GET /ca : 403 (financier reserve direction)")
    void shouldDenyCaForReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/ca").param("periode", "SEMAINE")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-FIN-001 - ADMIN GET /ca/export.xlsx : content-type XLSX")
    void shouldExportCaXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/ca/export.xlsx").param("periode", "SEMAINE")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE));
    }

    // ============================================================================
    // R-INV-001 Alertes stock
    // ============================================================================

    @Test
    @DisplayName("R-INV-001 - MAGASIN GET /stock-alerts : 200 + liste vide")
    void shouldGetStockAlertsForMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/stock-alerts")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("R-INV-001 - RECEPTION GET /stock-alerts : 403")
    void shouldDenyStockAlertsForReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/stock-alerts")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-INV-001 - MAGASIN GET /stock-alerts/export.xlsx : XLSX content-type")
    void shouldExportStockAlertsXlsx() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/stock-alerts/export.xlsx")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE));
    }

    // ============================================================================
    // R-NA-001 Night audit recap
    // ============================================================================

    @Test
    @DisplayName("R-NA-001 - ADMIN GET /night-audit avec from/to : 200 + liste")
    void shouldGetNightAuditRecapForAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/night-audit")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-04")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("R-NA-001 - MAGASIN GET /night-audit : 403")
    void shouldDenyNightAuditForMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/night-audit")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-02")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-NA-001 - GET /night-audit sans from : 400 (validation Spring)")
    void shouldRejectMissingFromParam() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/reports/night-audit")
                        .param("to", "2026-05-02")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadRequest());
    }

    // ============================================================================
    // R-CLI-001 Top clients
    // ============================================================================

    @Test
    @DisplayName("R-CLI-001 - RECEPTION GET /top-clients : 200 + tableau")
    void shouldGetTopClientsForReception() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/top-clients")
                        .param("from", "2026-01-01")
                        .param("to", "2027-01-01")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("R-CLI-001 - MAGASIN GET /top-clients : 403")
    void shouldDenyTopClientsForMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/reports/top-clients")
                        .param("from", "2026-01-01")
                        .param("to", "2027-01-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-CLI-001 - RECEPTION GET /top-clients/export.xlsx : XLSX content-type")
    void shouldExportTopClientsXlsx() throws Exception {
        String jwt = jwtFor(userReception);
        mockMvc.perform(get("/api/reports/top-clients/export.xlsx")
                        .param("from", "2026-01-01")
                        .param("to", "2027-01-01")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE));
    }
}
