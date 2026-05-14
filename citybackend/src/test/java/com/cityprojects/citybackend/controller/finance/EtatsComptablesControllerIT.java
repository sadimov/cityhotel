package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.StatutExercice;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
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
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test Failsafe du {@link EtatsComptablesController} (B5).
 *
 * <h3>Couverture</h3>
 * <ul>
 *   <li>GET balance / grand-livre / journal / bilan / compte-resultat : 200 JSON.</li>
 *   <li>Roles non autorises : 403.</li>
 *   <li>Exports XLSX et PDF : status 200 + content-type + body non vide.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class EtatsComptablesControllerIT {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF = "application/pdf";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JournalComptableRepository journalRepository;
    @Autowired private ExerciceRepository exerciceRepository;
    @Autowired private PlanComptableGeneralRepository pcgRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private DBUser userAdmin;
    private DBUser userMagasin;
    private Long hotelId;
    private Long journalId;
    private Long exerciceId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        savePcg("411100", "Clients", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("706110", "Ventes nuitees", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);

        Hotel h = new Hotel("MR1", "Hotel Mauritanie");
        h.setCodePays("MR");
        hotelId = hotelRepository.saveAndFlush(h).getHotelId();

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userAdmin = userRepository.saveAndFlush(buildUser("adminUser", "admin@h1.test", h, admin));
        userMagasin = userRepository.saveAndFlush(buildUser("magUser", "mag@h1.test", h, magasin));

        TenantContext.set(hotelId);
        try {
            JournalComptable j = new JournalComptable();
            j.setCode("VTE");
            j.setLibelle("Ventes");
            j.setType(TypeJournal.VENTE);
            j.setActif(Boolean.TRUE);
            journalId = journalRepository.saveAndFlush(j).getId();

            Exercice e = new Exercice();
            e.setCode("2026");
            e.setDateDebut(LocalDate.of(2026, 1, 1));
            e.setDateFin(LocalDate.of(2026, 12, 31));
            e.setStatut(StatutExercice.OUVERT);
            exerciceId = exerciceRepository.saveAndFlush(e).getId();
        } finally {
            TenantContext.clear();
        }

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
        jdbcTemplate.update("DELETE FROM finance.ligne_ecriture");
        jdbcTemplate.update("DELETE FROM finance.ecriture_comptable");
        jdbcTemplate.update("DELETE FROM finance.journal_comptable");
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.compte_mapping");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM finance.plan_comptable_general");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void savePcg(String code, String libelle, int classe, NatureCompte nature, SensNormal sens) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(sens);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        pcgRepository.saveAndFlush(p);
    }

    private static DBUser buildUser(String username, String email, Hotel hotel, Role role) {
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

    // ----------------------------------------------------------------------
    // BALANCE
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Balance - ADMIN : 200 JSON (wrapper success/data)")
    void balanceJson() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/finance/etats/balance")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lignes").isArray())
                .andExpect(jsonPath("$.data.totalDebit").exists());
    }

    @Test
    @DisplayName("Balance - MAGASIN : 403")
    void balanceForbidden() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(get("/api/finance/etats/balance")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Balance export XLSX - ADMIN : 200 + content-type + body > 0")
    void balanceXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/balance/export/xlsx")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    @DisplayName("Balance export PDF - ADMIN : 200 + content-type + body > 0")
    void balancePdf() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/balance/export/pdf")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    // ----------------------------------------------------------------------
    // GRAND LIVRE
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Grand Livre - ADMIN : 200 JSON")
    void grandLivreJson() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/finance/etats/grand-livre")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.comptes").isArray());
    }

    @Test
    @DisplayName("Grand Livre export XLSX - ADMIN : 200")
    void grandLivreXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/grand-livre/export/xlsx")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    @DisplayName("Grand Livre export PDF - ADMIN : 200")
    void grandLivrePdf() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/grand-livre/export/pdf")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    // ----------------------------------------------------------------------
    // JOURNAL
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Journal - ADMIN : 200 JSON")
    void journalJson() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/finance/etats/journal")
                        .param("journalId", journalId.toString())
                        .param("dateDebut", "2026-01-01")
                        .param("dateFin", "2026-12-31")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.journalCode").value("VTE"));
    }

    @Test
    @DisplayName("Journal export XLSX - ADMIN : 200")
    void journalXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/journal/export/xlsx")
                        .param("journalId", journalId.toString())
                        .param("dateDebut", "2026-01-01")
                        .param("dateFin", "2026-12-31")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    @DisplayName("Journal export PDF - ADMIN : 200")
    void journalPdf() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/journal/export/pdf")
                        .param("journalId", journalId.toString())
                        .param("dateDebut", "2026-01-01")
                        .param("dateFin", "2026-12-31")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    // ----------------------------------------------------------------------
    // BILAN
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Bilan - ADMIN : 200 JSON")
    void bilanJson() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/finance/etats/bilan")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalActif").exists())
                .andExpect(jsonPath("$.data.totalPassif").exists());
    }

    @Test
    @DisplayName("Bilan export XLSX - ADMIN : 200")
    void bilanXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/bilan/export/xlsx")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    @DisplayName("Bilan export PDF - ADMIN : 200")
    void bilanPdf() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/bilan/export/pdf")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    // ----------------------------------------------------------------------
    // COMPTE DE RESULTAT
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Compte de resultat - ADMIN : 200 JSON")
    void crJson() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(get("/api/finance/etats/compte-resultat")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultatNet").exists())
                .andExpect(jsonPath("$.data.margeBrute").exists());
    }

    @Test
    @DisplayName("Compte de resultat export XLSX - ADMIN : 200")
    void crXlsx() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/compte-resultat/export/xlsx")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    @DisplayName("Compte de resultat export PDF - ADMIN : 200")
    void crPdf() throws Exception {
        String jwt = jwtFor(userAdmin);
        MvcResult r = mockMvc.perform(get("/api/finance/etats/compte-resultat/export/pdf")
                        .param("exerciceId", exerciceId.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF))
                .andReturn();
        assertTrue(r.getResponse().getContentAsByteArray().length > 0);
    }
}
