package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration Failsafe sur {@link EcritureComptableController} (B2).
 *
 * <h3>Cas couverts</h3>
 * <ol>
 *   <li>T1 - POST /api/finance/ecritures avec ADMIN : 201, ecriture VALIDEE,
 *       numero JRN-VTE genere.</li>
 *   <li>T2 - POST /api/finance/ecritures avec GERANT : 403 (creation reservee
 *       SUPERADMIN/ADMIN).</li>
 *   <li>T3 - POST /api/finance/ecritures avec MAGASIN : 403.</li>
 *   <li>T4 - POST /api/finance/ecritures avec D != C : 400 (validation
 *       BusinessException).</li>
 *   <li>T5 - GET /api/finance/ecritures sans JWT : 401.</li>
 *   <li>T6 - POST /api/finance/ecritures/{id}/contre-passer avec ADMIN : 200,
 *       nouvelle ecriture creee avec sens inverses.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class EcritureComptableControllerIT {

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
    private JournalComptableRepository journalRepository;

    @Autowired
    private PlanComptableGeneralRepository pcgRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private DBUser userAdmin;
    private DBUser userGerant;
    private DBUser userMagasin;
    private Long hotelId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanAll();

        // Seed PCG minimal
        savePcg("411100", "Clients particuliers", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("706100", "Ventes - nuitees hebergement", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);

        Hotel h = new Hotel("MR1", "Hotel Mauritanie");
        h.setCodePays("MR");
        hotelId = hotelRepository.saveAndFlush(h).getHotelId();

        Role admin = roleRepository.saveAndFlush(new Role("ADMIN", "Admin"));
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userAdmin = userRepository.saveAndFlush(buildUser("adminUser", "admin@h1.test", h, admin));
        userGerant = userRepository.saveAndFlush(buildUser("gerantUser", "gerant@h1.test", h, gerant));
        userMagasin = userRepository.saveAndFlush(buildUser("magUser", "mag@h1.test", h, magasin));

        // Seed journal VTE dans l'hotel
        TenantContext.set(hotelId);
        try {
            JournalComptable j = new JournalComptable();
            j.setCode("VTE");
            j.setLibelle("Ventes");
            j.setType(TypeJournal.VENTE);
            j.setActif(Boolean.TRUE);
            journalRepository.saveAndFlush(j);
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

    /** Construit un payload d'ecriture equilibree (D=C=100). */
    private static Map<String, Object> okPayload() {
        return Map.of(
                "dateComptable", "2026-03-15",
                "datePiece", "2026-03-15",
                "journalCode", "VTE",
                "libelle", "Vente facture test IT",
                "reference", "FACT-2026-MR-000001",
                "lignes", java.util.List.of(
                        Map.of("compteCode", "411100", "sens", "DEBIT", "montant", "100.00"),
                        Map.of("compteCode", "706100", "sens", "CREDIT", "montant", "100.00")
                ));
    }

    @Test
    @DisplayName("T1 - POST /api/finance/ecritures avec ADMIN : 201, ecriture VALIDEE")
    void shouldCreateEcritureAsAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);
        mockMvc.perform(post("/api/finance/ecritures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okPayload())))
                .andExpect(status().isCreated())
                // ApiResponseBodyAdvice wrappe en { success, data }
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.numero").exists())
                .andExpect(jsonPath("$.data.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.data.journalCode").value("VTE"))
                .andExpect(jsonPath("$.data.totalDebit").value(100.00))
                .andExpect(jsonPath("$.data.totalCredit").value(100.00))
                .andExpect(jsonPath("$.data.lignes.length()").value(2));
    }

    @Test
    @DisplayName("T2 - POST /api/finance/ecritures avec GERANT : 403")
    void shouldDenyCreateAsGerant() throws Exception {
        String jwt = jwtFor(userGerant);
        mockMvc.perform(post("/api/finance/ecritures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okPayload())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T3 - POST /api/finance/ecritures avec MAGASIN : 403")
    void shouldDenyCreateAsMagasin() throws Exception {
        String jwt = jwtFor(userMagasin);
        mockMvc.perform(post("/api/finance/ecritures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okPayload())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T4 - POST avec D != C : 400 (BusinessException unbalanced)")
    void shouldRejectUnbalanced() throws Exception {
        String jwt = jwtFor(userAdmin);
        Map<String, Object> payload = Map.of(
                "dateComptable", "2026-03-15",
                "datePiece", "2026-03-15",
                "journalCode", "VTE",
                "libelle", "Test desequilibre",
                "reference", "X",
                "lignes", java.util.List.of(
                        Map.of("compteCode", "411100", "sens", "DEBIT", "montant", "100.00"),
                        Map.of("compteCode", "706100", "sens", "CREDIT", "montant", "90.00")
                ));
        mockMvc.perform(post("/api/finance/ecritures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("T5 - GET /api/finance/ecritures sans JWT : 401")
    void shouldDenyGetWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/finance/ecritures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T6 - POST /{id}/contre-passer avec ADMIN : 200, ecriture inversee")
    void shouldContrePasserAsAdmin() throws Exception {
        String jwt = jwtFor(userAdmin);

        // Cree l'ecriture source
        String responseSource = mockMvc.perform(post("/api/finance/ecritures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okPayload())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Recupere l'id depuis la reponse
        Long sourceId = ((Number) objectMapper.readTree(responseSource).at("/data/id").numberValue()).longValue();

        // Contre-passation
        mockMvc.perform(post("/api/finance/ecritures/" + sourceId + "/contre-passer")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Test contre-passation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.data.ecritureSourceId").value(sourceId.intValue()))
                .andExpect(jsonPath("$.data.reference").exists())
                // Sens inverses : la 1ere ligne etait DEBIT 411100, la nouvelle a CREDIT 411100
                .andExpect(jsonPath("$.data.lignes[0].compteCode").value("411100"))
                .andExpect(jsonPath("$.data.lignes[0].sens").value("CREDIT"))
                .andExpect(jsonPath("$.data.lignes[1].compteCode").value("706100"))
                .andExpect(jsonPath("$.data.lignes[1].sens").value("DEBIT"));
    }
}
