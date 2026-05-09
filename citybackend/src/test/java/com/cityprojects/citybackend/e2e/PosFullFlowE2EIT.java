package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E (Failsafe) du flux POS complet avec table assignee + cloture caisse
 * (Tour 26.1).
 *
 * <h3>Scenario "Full flow + Z de caisse"</h3>
 * <ol>
 *   <li>Ouvrir table T5 : POST commande COMPTANT 2 plats * 1500 = 3000 MRU,
 *       avec {@code numeroTable="T5"}.</li>
 *   <li>Cycle de service : VALIDEE -&gt; EN_PREPARATION -&gt; PRETE -&gt; SERVIE.
 *       Pas de recette definie -&gt; transition SERVIE sans BS.</li>
 *   <li>Encaisser cette commande comptant en ESPECES : 3000 MRU.</li>
 *   <li>2eme commande sur table T5 (split-bill du second tour de service) :
 *       1 plat * 1500 = 1500 MRU, encaisse en BANKILY.</li>
 *   <li>Cloture caisse : GET /api/restaurant/caisse/cloture?date=AUJOURDHUI
 *       doit retourner :
 *       <ul>
 *         <li>ESPECES : 3000 / 1 transaction</li>
 *         <li>BANKILY : 1500 / 1 transaction</li>
 *         <li>totalGlobal : 4500</li>
 *         <li>nbTransactionsTotal : 2</li>
 *         <li>nbCommandesEncaissees : 2 (les 2 commandes ont factureId set)</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Pourquoi 2 commandes au lieu d'un vrai split-payment</h3>
 * <p>{@code encaisserComptant} exige {@code dto.montant() == commande.montantTtc}
 * (paiement integral, pas partiel). Pour simuler un split-bill, le pattern POS
 * pratique est de creer 2 commandes successives sur la meme table (pattern
 * "second tour de service"). C'est ce que fait ce test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PosFullFlowE2EIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategorieMenuRepository categorieRepository;
    @Autowired private ArticleMenuRepository articleRepository;
    @Autowired private CommandeRepository commandeRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private LigneFactureRepository ligneFactureRepository;
    @Autowired private PaiementRepository paiementRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate tx;
    private DBUser userGerant;
    private Long hotelMrId;
    private Long articleMrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Sidi", "Mohamed", mr, gerant));

        try {
            TenantContext.set(hotelMrId);
            CategorieMenu cat = tx.execute(s -> {
                CategorieMenu c = new CategorieMenu();
                c.setNom("Plats");
                c.setOrdre(0);
                c.setActif(Boolean.TRUE);
                return categorieRepository.save(c);
            });
            ArticleMenu art = tx.execute(s -> {
                ArticleMenu a = new ArticleMenu();
                a.setCodeArticle("PLT1");
                a.setNom("Riz au poisson");
                a.setCategorieId(cat.getCategorieId());
                a.setPrix(BigDecimal.valueOf(1500));
                a.setActif(Boolean.TRUE);
                a.setDisponible(Boolean.TRUE);
                a.setStatut(StatutArticle.ACTIF);
                return articleRepository.save(a);
            });
            articleMrId = art.getArticleId();
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
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM restaurant.tickets");
        jdbcTemplate.update("DELETE FROM restaurant.lignes_commande");
        jdbcTemplate.update("DELETE FROM restaurant.commandes");
        jdbcTemplate.update("DELETE FROM restaurant.recettes_articles");
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
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

    /** Ouvre une commande COMPTANT sur la table donnee, 2 lignes du meme article. */
    private Long openTableCommande(String jwt, String numeroTable, int quantite) throws Exception {
        String body = "{"
                + "\"modeReglement\":\"COMPTANT\","
                + "\"devise\":\"MRU\","
                + "\"numeroTable\":\"" + numeroTable + "\","
                + "\"lignes\":["
                + "  {\"articleId\":" + articleMrId + ",\"quantite\":" + quantite + "}"
                + "]"
                + "}";
        String resp = mockMvc.perform(post("/api/restaurant/commandes")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroTable").value(numeroTable))
                .andReturn().getResponse().getContentAsString();
        return readJsonLong(resp, "$.commandeId");
    }

    private void advanceToServie(String jwt, Long commandeId) throws Exception {
        for (StatutCommande next : List.of(StatutCommande.VALIDEE,
                StatutCommande.EN_PREPARATION, StatutCommande.PRETE, StatutCommande.SERVIE)) {
            mockMvc.perform(post("/api/restaurant/commandes/" + commandeId + "/statut/" + next)
                            .header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isOk());
        }
    }

    private void encaisser(String jwt, Long commandeId, String mode, BigDecimal montant) throws Exception {
        String body = "{"
                + "\"modePaiement\":\"" + mode + "\","
                + "\"montant\":" + montant.toPlainString() + ","
                + "\"referencePaiement\":\"REF-" + mode + "-" + commandeId + "\""
                + "}";
        mockMvc.perform(post("/api/restaurant/commandes/" + commandeId + "/encaisser")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Full flow POS table T5 + 2 commandes encaissees ESPECES/BANKILY + cloture caisse 4500 MRU sur 2 transactions")
    void shouldRunFullPosFlowAndCloseCashbook() throws Exception {
        String jwt = jwtFor(userGerant);
        LocalDate today = LocalDate.now();

        // === COMMANDE 1 sur table T5 : 2 plats = 3000 MRU, encaissee ESPECES ===
        Long c1 = openTableCommande(jwt, "T5", 2);
        advanceToServie(jwt, c1);
        encaisser(jwt, c1, "ESPECES", new BigDecimal("3000.00"));

        // === COMMANDE 2 sur table T5 (split-bill 2eme tour) : 1 plat = 1500 MRU, encaissee BANKILY ===
        Long c2 = openTableCommande(jwt, "T5", 1);
        advanceToServie(jwt, c2);
        encaisser(jwt, c2, "BANKILY", new BigDecimal("1500.00"));

        // === Verifications JPA : 2 commandes T5 facturees + 2 paiements VALIDES ===
        tx.execute(s -> {
            TenantContext.set(hotelMrId);
            try {
                Commande commande1 = commandeRepository.findById(c1).orElseThrow();
                assertThat(commande1.getNumeroTable()).isEqualTo("T5");
                assertThat(commande1.getFactureId()).isNotNull();
                assertThat(commande1.getMontantPaye()).isEqualByComparingTo(new BigDecimal("3000.00"));

                Commande commande2 = commandeRepository.findById(c2).orElseThrow();
                assertThat(commande2.getNumeroTable()).isEqualTo("T5");
                assertThat(commande2.getFactureId()).isNotNull();
                assertThat(commande2.getMontantPaye()).isEqualByComparingTo(new BigDecimal("1500.00"));

                List<Paiement> paiements = paiementRepository.findAll();
                assertThat(paiements).hasSize(2);
                assertThat(paiements).allMatch(p -> p.getStatut() == StatutPaiement.VALIDE);
                return null;
            } finally {
                TenantContext.clear();
            }
        });

        // === CLOTURE CAISSE === GET /api/restaurant/caisse/cloture?date=YYYY-MM-DD
        String clotureBody = mockMvc.perform(get("/api/restaurant/caisse/cloture")
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.hotelId").value(hotelMrId))
                .andExpect(jsonPath("$.totalGlobal").value(4500))
                .andExpect(jsonPath("$.nbTransactionsTotal").value(2))
                .andExpect(jsonPath("$.nbCommandesEncaissees").value(2))
                .andExpect(jsonPath("$.nbCommandesAnnulees").value(0))
                .andExpect(jsonPath("$.totauxParMode.ESPECES.montant").value(3000))
                .andExpect(jsonPath("$.totauxParMode.ESPECES.nombre").value(1))
                .andExpect(jsonPath("$.totauxParMode.BANKILY.montant").value(1500))
                .andExpect(jsonPath("$.totauxParMode.BANKILY.nombre").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(clotureBody).contains("\"ESPECES\"").contains("\"BANKILY\"");
    }
}
