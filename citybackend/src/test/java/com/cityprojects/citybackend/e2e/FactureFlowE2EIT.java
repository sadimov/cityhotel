package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.TypeOperationCompte;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.OperationCompteRepository;
import com.cityprojects.citybackend.security.JwtTokenProvider;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E (Failsafe) du cycle de vie complet d'une facture multi-lignes
 * avec multi-paiements et audit trail comptable auxiliaire (Tour 22.1).
 *
 * <h3>Pourquoi un package {@code e2e/} dedie</h3>
 * <p>Identique a {@link ReservationFlowE2EIT} et {@link InventoryFlowE2EIT} :
 * scenario cross-controller (clients + finance/factures + finance/paiements)
 * + verifications via repositories injectes pour les entites comptables
 * auxiliaires (compte, operations) qui n'ont pas de controller dedie au
 * Tour 22.1.</p>
 *
 * <h3>Flux teste (T1)</h3>
 * <ol>
 *   <li>POST {@code /api/clients} : creation d'un client (numero CLI auto-genere).</li>
 *   <li>POST {@code /api/finance/factures} : creation facture BROUILLON, 2 lignes
 *       DIVERS pour un total de 5500 MRU (5000 + 500).</li>
 *   <li>POST {@code /api/finance/factures/{id}/emettre} : transition BROUILLON
 *       -&gt; EMISE.</li>
 *   <li>Verifie via {@link CompteRepository} : compte auxiliaire CPT-CLI-{id}
 *       cree avec {@code soldeActuel = 5500}, et 1 OperationCompte DEBIT
 *       de 5500 referencant la facture.</li>
 *   <li>POST {@code /api/finance/paiements} : 1er paiement BANKILY de 1500 MRU
 *       sur la facture -&gt; statut PARTIELLEMENT_PAYEE, soldeActuel = 4000,
 *       1 OperationCompte CREDIT de 1500.</li>
 *   <li>POST {@code /api/finance/paiements} : 2e paiement CARTE_BANCAIRE de
 *       4000 MRU -&gt; statut PAYEE, soldeActuel = 0, 1 OperationCompte CREDIT
 *       de 4000.</li>
 *   <li>Verifie le journal complet : 1 DEBIT + 2 CREDIT, somme DEBIT = somme
 *       CREDIT, dernier soldeApres = 0.</li>
 * </ol>
 *
 * <h3>Doctrine Tour 20bis</h3>
 * <p>Les entites {@link Compte}/{@link OperationCompte} sont
 * {@code @Deprecated} (renommage prevu : audit trail auxiliaire client, pas
 * de partie double SYSCOHADA). La doctrine actée Tour 20bis stipule que la
 * compta generale est externalisee vers Dolibarr (bridge a venir tour
 * ulterieur). Ce test valide donc UNIQUEMENT l'audit trail auxiliaire client.
 * Le {@code @SuppressWarnings("deprecation")} sur la classe est volontaire.</p>
 *
 * <h3>Pas de @Transactional sur la classe</h3>
 * <p>Pattern identique a {@code InventoryFlowE2EIT} et {@code ReservationFlowE2EIT}.
 * Cleanup explicite en {@link #setUp()} / {@link #tearDown()}.</p>
 */
@SuppressWarnings("deprecation")
@SpringBootTest
@ActiveProfiles("test")
class FactureFlowE2EIT {

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
    private CompteRepository compteRepository;

    @Autowired
    private OperationCompteRepository operationCompteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private MockMvc mockMvc;
    private DBUser userGerant;
    private Long hotelMrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        // GERANT couvre creation client + creation/emission facture + creation paiement.
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Sidi", "Cheikh", mr, gerant));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    /**
     * Cleanup ordonne (FK strict). On nettoie tout finance + clients + core.
     */
    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
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

    private static Long readJsonLong(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value instanceof Number n) ? n.longValue() : null;
    }

    private static String readJsonString(String body, String path) {
        Object value = JsonPath.read(body, path);
        return (value != null) ? value.toString() : null;
    }

    private Long createClient(String jwt) throws Exception {
        String body = "{"
                + "\"prenom\":\"Sidi\","
                + "\"nom\":\"Mohamed\","
                + "\"telephone\":\"+22245100200\","
                + "\"email\":\"sidi.mohamed@example.mr\""
                + "}";
        String response = mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJsonLong(response, "$.clientId");
    }

    /**
     * Cree une facture multi-lignes via l'API (clientId + 2 lignes DIVERS pour
     * un total controlable). On evite le flux nuitee/produit pour focaliser le
     * test sur la chaine facture+paiement+compta auxiliaire (cf. note du tour :
     * "Si la création de réservation+check-in est complexe à reproduire ici,
     * simplifie").
     */
    private String createFacture(String jwt, Long clientId) throws Exception {
        String body = "{"
                + "\"clientId\":" + clientId + ","
                + "\"devise\":\"MRU\","
                + "\"lignes\":["
                + "  {\"typeLigne\":\"DIVERS\",\"libelle\":\"Service hotellerie\","
                + "   \"quantite\":1,\"prixUnitaire\":5000.00,\"tauxTva\":0},"
                + "  {\"typeLigne\":\"DIVERS\",\"libelle\":\"Mini-bar\","
                + "   \"quantite\":5,\"prixUnitaire\":100.00,\"tauxTva\":0}"
                + "]"
                + "}";
        return mockMvc.perform(post("/api/finance/factures")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String emettreFacture(String jwt, Long factureId) throws Exception {
        return mockMvc.perform(post("/api/finance/factures/" + factureId + "/emettre")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createPaiement(String jwt, Long factureId, String montant, String mode,
                                  String reference) throws Exception {
        String refJson = (reference != null) ? "\"" + reference + "\"" : "null";
        String body = "{"
                + "\"factureId\":" + factureId + ","
                + "\"montantTotal\":" + montant + ","
                + "\"devise\":\"MRU\","
                + "\"modePaiement\":\"" + mode + "\","
                + "\"referencePaiement\":" + refJson
                + "}";
        return mockMvc.perform(post("/api/finance/paiements")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Lit une entite Compte par clientId via repo (pas d'endpoint dedie au Tour 22.1). */
    private Compte readCompte(Long clientId) {
        return transactionTemplate.execute(t -> {
            TenantContext.set(hotelMrId);
            try {
                return compteRepository.findByClientId(clientId).orElse(null);
            } finally {
                TenantContext.clear();
            }
        });
    }

    private List<OperationCompte> readOperations(Long compteId) {
        return transactionTemplate.execute(t -> {
            TenantContext.set(hotelMrId);
            try {
                return operationCompteRepository
                        .findByCompteIdOrderByDateOperationDesc(compteId);
            } finally {
                TenantContext.clear();
            }
        });
    }

    @Test
    @DisplayName("T1 - Cycle complet facture multi-lignes + multi-paiements + comptabilite auxiliaire (1 DEBIT + 2 CREDIT, solde final = 0)")
    void shouldCompleteFactureLifecycleWithMultiPaymentsAndAuxiliaryAccounting() throws Exception {
        String jwt = jwtFor(userGerant);

        // ---------- 1) CLIENT ----------
        Long clientId = createClient(jwt);
        assertThat(clientId).isPositive();

        // ---------- 2) FACTURE BROUILLON 5500 MRU (2 lignes) ----------
        String factureBody = createFacture(jwt, clientId);
        Long factureId = readJsonLong(factureBody, "$.factureId");
        String numeroFacture = readJsonString(factureBody, "$.numeroFacture");
        BigDecimal totalTtc = new BigDecimal(readJsonString(factureBody, "$.montantTtc"));
        assertThat(numeroFacture).matches("FACT-\\d{4}-MR-000001");
        assertThat(totalTtc).isEqualByComparingTo(new BigDecimal("5500.00"));

        // A ce stade, statut BROUILLON : aucun compte ni operation (DEBIT pose
        // a l'emission, pas a la creation).
        assertThat(readCompte(clientId)).isNull();

        // ---------- 3) EMISSION ----------
        String emiseBody = emettreFacture(jwt, factureId);
        assertThat(readJsonString(emiseBody, "$.statut")).isEqualTo("EMISE");

        // ---------- 4) Compte auxiliaire cree + DEBIT ----------
        Compte compte = readCompte(clientId);
        assertThat(compte).isNotNull();
        assertThat(compte.getNumeroCompte()).isEqualTo("CPT-CLI-" + clientId);
        assertThat(compte.getSoldeActuel()).isEqualByComparingTo(new BigDecimal("5500.00"));

        List<OperationCompte> opsApresEmission = readOperations(compte.getCompteId());
        assertThat(opsApresEmission).hasSize(1);
        OperationCompte debit = opsApresEmission.get(0);
        assertThat(debit.getTypeOperation()).isEqualTo(TypeOperationCompte.DEBIT);
        assertThat(debit.getMontant()).isEqualByComparingTo(new BigDecimal("5500.00"));
        assertThat(debit.getSoldeAvant()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(debit.getSoldeApres()).isEqualByComparingTo(new BigDecimal("5500.00"));
        assertThat(debit.getFactureId()).isEqualTo(factureId);

        // ---------- 5) PAIEMENT #1 BANKILY 1500 MRU ----------
        String paiement1Body = createPaiement(jwt, factureId, "1500.00", "BANKILY", "REF-BNK-001");
        Long paiement1Id = readJsonLong(paiement1Body, "$.paiementId");
        assertThat(readJsonString(paiement1Body, "$.numeroPaiement"))
                .matches("PAY-\\d{4}-MR-000001");

        // Compte rafraichi : solde = 5500 - 1500 = 4000
        Compte apres1 = readCompte(clientId);
        assertThat(apres1.getSoldeActuel()).isEqualByComparingTo(new BigDecimal("4000.00"));

        List<OperationCompte> opsApres1 = readOperations(compte.getCompteId());
        assertThat(opsApres1).hasSize(2);
        // Tri DESC sur dateOperation : le CREDIT le plus recent en tete
        OperationCompte credit1 = opsApres1.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.CREDIT)
                .findFirst().orElseThrow();
        assertThat(credit1.getMontant()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(credit1.getSoldeAvant()).isEqualByComparingTo(new BigDecimal("5500.00"));
        assertThat(credit1.getSoldeApres()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(credit1.getPaiementId()).isEqualTo(paiement1Id);

        // ---------- 6) PAIEMENT #2 CARTE_BANCAIRE 4000 MRU ----------
        String paiement2Body = createPaiement(jwt, factureId, "4000.00", "CARTE_BANCAIRE", "TXN-VS-002");
        Long paiement2Id = readJsonLong(paiement2Body, "$.paiementId");
        assertThat(readJsonString(paiement2Body, "$.numeroPaiement"))
                .matches("PAY-\\d{4}-MR-000002");

        // Compte rafraichi : solde = 4000 - 4000 = 0
        Compte apres2 = readCompte(clientId);
        assertThat(apres2.getSoldeActuel()).isEqualByComparingTo(BigDecimal.ZERO);

        // ---------- 7) Journal complet : 1 DEBIT + 2 CREDIT, somme = 0 ----------
        List<OperationCompte> journal = readOperations(compte.getCompteId());
        assertThat(journal).hasSize(3);

        long nbDebit = journal.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.DEBIT)
                .count();
        long nbCredit = journal.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.CREDIT)
                .count();
        assertThat(nbDebit).isEqualTo(1);
        assertThat(nbCredit).isEqualTo(2);

        BigDecimal sommeDebit = journal.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.DEBIT)
                .map(OperationCompte::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sommeCredit = journal.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.CREDIT)
                .map(OperationCompte::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sommeDebit).isEqualByComparingTo(sommeCredit);
        assertThat(sommeDebit).isEqualByComparingTo(new BigDecimal("5500.00"));

        // Le 2eme paiement reference paiement2Id et avait soldeApres = 0
        OperationCompte credit2 = journal.stream()
                .filter(o -> o.getTypeOperation() == TypeOperationCompte.CREDIT)
                .filter(o -> paiement2Id.equals(o.getPaiementId()))
                .findFirst().orElseThrow();
        assertThat(credit2.getSoldeApres()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
