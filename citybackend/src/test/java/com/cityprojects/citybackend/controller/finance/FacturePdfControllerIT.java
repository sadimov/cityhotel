package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test Failsafe du endpoint {@code GET /api/finance/factures/{id}/pdf}
 * (Bloc B6).
 *
 * <h3>Couverture</h3>
 * <ul>
 *   <li>T1 - GERANT : 200 + content-type application/pdf + body &gt; 0
 *       + Content-Disposition: attachment avec filename FACTURE-...pdf.</li>
 *   <li>T2 - Facture inexistante : 404.</li>
 *   <li>T3 - Sans JWT : 401.</li>
 *   <li>T4 - MENAGE (role non autorise) : 403.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class FacturePdfControllerIT {

    private static final String PDF = "application/pdf";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;
    private DBUser userGerant;
    private DBUser userMenage;
    private Long hotelId;
    private Long factureId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanAll();

        Hotel h = new Hotel("MR1", "Hotel Mauritanie");
        h.setCodePays("MR");
        h.setHotelAdresse("Avenue Gamal Abdel Nasser");
        h.setVille("Nouakchott");
        h.setPays("Mauritanie");
        h.setHotelTel("+222 45 25 25 25");
        h.setEmail("contact@hotel-mr.test");
        h.setNif("NIF-HOTEL-1234567");
        hotelId = hotelRepository.saveAndFlush(h).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role menage = roleRepository.saveAndFlush(new Role("MENAGE", "Menage"));

        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant", "gerant@h1.test", h, gerant));
        userMenage = userRepository.saveAndFlush(buildUser(
                "menage", "menage@h1.test", h, menage));

        factureId = createFacture(hotelId, userGerant.getUserId());

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
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private DBUser buildUser(String username, String email, Hotel hotel, Role role) {
        DBUser user = new DBUser(username, email,
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Test", "User", hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return user;
    }

    private String jwtFor(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        return jwtTokenProvider.generateTokenForUser(principal);
    }

    private Long createFacture(Long tenantId, Long userId) {
        try {
            TenantContext.set(tenantId);
            return transactionTemplate.execute(s -> {
                Facture f = new Facture();
                f.setNumeroFacture("FACT-2026-MR-000001");
                f.setTypeFacture(TypeFacture.FACTURE);
                f.setDateFacture(LocalDate.of(2026, 5, 6));
                f.setDateEcheance(LocalDate.of(2026, 6, 5));
                f.setStatut(StatutFacture.EMISE);
                f.setDevise("MRU");
                f.setUserId(userId);
                f.setMontantHt(new BigDecimal("10000.00"));
                f.setMontantTva(new BigDecimal("0.00"));
                f.setMontantTtc(new BigDecimal("10000.00"));
                f.setMontantPaye(BigDecimal.ZERO);
                return factureRepository.save(f).getFactureId();
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - GERANT : 200 + content-type pdf + body > 0 + Content-Disposition attachment")
    void pdfGenereParGerant() throws Exception {
        String jwt = jwtFor(userGerant);
        MvcResult r = mockMvc.perform(get("/api/finance/factures/" + factureId + "/pdf")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] body = r.getResponse().getContentAsByteArray();
        assertTrue(body.length > 0, "Le PDF doit etre non vide");

        String cd = r.getResponse().getHeader("Content-Disposition");
        assertTrue(cd != null && cd.contains("attachment"), "Doit etre une piece jointe");
        assertTrue(cd.contains("FACTURE-FACT-2026-MR-000001"),
                "Le filename doit contenir le numero de facture");
        assertTrue(cd.endsWith(".pdf") || cd.contains(".pdf"),
                "Le filename doit se terminer par .pdf");

        // Verification basique du header PDF
        assertTrue(body.length >= 4 && body[0] == '%' && body[1] == 'P'
                        && body[2] == 'D' && body[3] == 'F',
                "Le body doit commencer par %PDF");
    }

    @Test
    @DisplayName("T2 - Facture inexistante : 404")
    void factureInexistante() throws Exception {
        String jwt = jwtFor(userGerant);
        mockMvc.perform(get("/api/finance/factures/999999/pdf")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T3 - Sans authentification : 401")
    void sansAuth() throws Exception {
        mockMvc.perform(get("/api/finance/factures/" + factureId + "/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T4 - MENAGE : 403 (matrice de roles n'inclut pas MENAGE)")
    void roleMenageInterdit() throws Exception {
        String jwt = jwtFor(userMenage);
        mockMvc.perform(get("/api/finance/factures/" + factureId + "/pdf")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }
}
