package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CategorieMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.TicketRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E (Failsafe) du cycle de vie complet d'une commande POS restaurant
 * (Tour 24).
 *
 * <h3>Pourquoi un package {@code e2e/} dedie</h3>
 * <p>Identique a {@link FactureFlowE2EIT} et {@link ReservationFlowE2EIT} :
 * scenario cross-controller (clients + restaurant/commandes + restaurant/tickets
 * + finance/factures auxiliaires) + verifications via repositories injectes.</p>
 *
 * <h3>Flux teste T1 - encaissement comptant</h3>
 * <ol>
 *   <li>POST {@code /api/clients} : creation client.</li>
 *   <li>POST {@code /api/restaurant/commandes} : commande COMPTANT 2 lignes.</li>
 *   <li>POST {@code /api/restaurant/commandes/{id}/encaisser} : encaissement
 *       BANKILY 4500 MRU.</li>
 *   <li>POST {@code /api/restaurant/tickets/commande/{id}/caisse} : ticket caisse.</li>
 *   <li>Verifie via repos : Facture EMISE/PAYEE + Paiement VALIDE +
 *       LigneFacture.commandeId set + Ticket CAISSE present.</li>
 * </ol>
 *
 * <h3>Flux teste T2 - reporte sur chambre</h3>
 * <ol>
 *   <li>Cree client + reservation ARRIVEE en JPA direct.</li>
 *   <li>POST commande REPORTE_CHAMBRE -&gt; commande.reservationId set.</li>
 *   <li>Verifie : aucune Facture creee (la facture sejour viendra au
 *       check-out, hors scope Tour 24).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PosFlowE2EIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Autowired private DBUserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategorieMenuRepository categorieRepository;
    @Autowired private ArticleMenuRepository articleRepository;
    @Autowired private CommandeRepository commandeRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private LigneFactureRepository ligneFactureRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ReservationRepository reservationRepository;

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

        // GERANT a tous les droits (clients + restaurant + finance) : on simplifie.
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userGerant = userRepository.saveAndFlush(buildUser(
                "gerant1", "gerant1@mr.test", "Sidi", "Mohamed", mr, gerant));

        // Catalogue MR : 1 categorie + 1 article a 1500 MRU
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
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
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

    @Test
    @DisplayName("T1 - Cycle complet POS comptant : client + commande 2 lignes + encaissement BANKILY + ticket caisse")
    void shouldCompleteComptantPosFlow() throws Exception {
        String jwt = jwtFor(userGerant);

        // ---------- 1) CLIENT ----------
        Long clientId = createClient(jwt);
        assertThat(clientId).isPositive();

        // ---------- 2) COMMANDE COMPTANT 2 lignes ----------
        // 2 * 1500 + 1 * 1500 = 4500 MRU
        String commandeBody = "{"
                + "\"modeReglement\":\"COMPTANT\","
                + "\"clientId\":" + clientId + ","
                + "\"devise\":\"MRU\","
                + "\"lignes\":["
                + "  {\"articleId\":" + articleMrId + ",\"quantite\":2},"
                + "  {\"articleId\":" + articleMrId + ",\"quantite\":1,\"notesCuisine\":\"sans coriandre\"}"
                + "]"
                + "}";

        String createResp = mockMvc.perform(post("/api/restaurant/commandes")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(commandeBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long commandeId = readJsonLong(createResp, "$.commandeId");
        assertThat(commandeId).isPositive();

        // ---------- 3) ENCAISSEMENT BANKILY 4500 MRU ----------
        String encaisseBody = "{"
                + "\"modePaiement\":\"BANKILY\","
                + "\"montant\":4500.00,"
                + "\"referencePaiement\":\"REF-BNK-001\""
                + "}";

        String encaisseResp = mockMvc.perform(post("/api/restaurant/commandes/" + commandeId + "/encaisser")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(encaisseBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long factureId = readJsonLong(encaisseResp, "$.factureId");
        assertThat(factureId).isNotNull().isPositive();

        // ---------- 4) TICKET CAISSE ----------
        mockMvc.perform(post("/api/restaurant/tickets/commande/" + commandeId + "/caisse")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated());

        // ---------- 5) Verifications via repos ----------
        tx.execute(s -> {
            TenantContext.set(hotelMrId);
            try {
                Commande commande = commandeRepository.findById(commandeId).orElseThrow();
                assertThat(commande.getFactureId()).isEqualTo(factureId);
                assertThat(commande.getMontantPaye()).isEqualByComparingTo(new BigDecimal("4500.00"));
                assertThat(commande.getModeReglement()).isEqualTo(ModeReglementCommande.COMPTANT);

                Facture facture = factureRepository.findById(factureId).orElseThrow();
                // total ttc = 4500 ; comme le paiement est de 4500 = montantTtc => statut PAYEE
                assertThat(facture.getStatut()).isEqualTo(StatutFacture.PAYEE);
                assertThat(facture.getMontantTtc()).isEqualByComparingTo(new BigDecimal("4500.00"));
                assertThat(facture.getClientId()).isEqualTo(clientId);

                // 2 lignes facture, toutes typees COMMANDE et liees a la commande
                List<LigneFacture> lignes = ligneFactureRepository
                        .findByFactureIdOrderByLigneFactureIdAsc(factureId);
                assertThat(lignes).hasSize(2);
                for (LigneFacture l : lignes) {
                    assertThat(l.getTypeLigne()).isEqualTo(TypeLigneFacture.COMMANDE);
                    assertThat(l.getCommandeId()).isEqualTo(commandeId);
                }

                // 1 paiement VALIDE de 4500
                var paiements = paiementRepository.findAll();
                assertThat(paiements).hasSize(1);
                var p = paiements.get(0);
                assertThat(p.getStatut()).isEqualTo(StatutPaiement.VALIDE);
                assertThat(p.getMontantTotal()).isEqualByComparingTo(new BigDecimal("4500.00"));

                // 1 ticket CAISSE
                var tickets = ticketRepository.findByCommandeIdOrderByDateImpressionDesc(commandeId);
                assertThat(tickets).hasSize(1);
                assertThat(tickets.get(0).getTypeTicket()).isEqualTo(
                        com.cityprojects.citybackend.entity.restaurant.TypeTicket.CAISSE);
                return null;
            } finally {
                TenantContext.clear();
            }
        });
    }

    @Test
    @DisplayName("T2 - Commande REPORTE_CHAMBRE : reservationId set, modeReglement=REPORTE_CHAMBRE, pas de Facture")
    void shouldCreateReporteChambreCommande() throws Exception {
        String jwt = jwtFor(userGerant);
        Long clientId = createClient(jwt);

        // Cree directement (JPA) une reservation ARRIVEE pour eviter le flux complet
        // chambre/check-in (couvert par ReservationFlowE2EIT). Pattern identique
        // a ReservationControllerIT.createReservationInTenant : TenantContext.set
        // AVANT tx.execute, clear en finally a la sortie.
        Long reservationId;
        try {
            TenantContext.set(hotelMrId);
            reservationId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-MR-999998");
                r.setClientPrincipalId(clientId);
                r.setDateArrivee(LocalDate.now().minusDays(1));
                r.setDateDepart(LocalDate.now().plusDays(2));
                r.setNbAdultes(1);
                r.setNbEnfants(0);
                r.setStatut(StatutReservation.ARRIVEE);
                r.setReductionPourcentage(BigDecimal.ZERO);
                r.setMontantTotal(BigDecimal.ZERO);
                r.setUserId(userGerant.getUserId());
                return reservationRepository.saveAndFlush(r).getReservationId();
            });
        } finally {
            TenantContext.clear();
        }

        // POST commande REPORTE_CHAMBRE
        String body = "{"
                + "\"modeReglement\":\"REPORTE_CHAMBRE\","
                + "\"clientId\":" + clientId + ","
                + "\"reservationId\":" + reservationId + ","
                + "\"devise\":\"MRU\","
                + "\"lignes\":[{\"articleId\":" + articleMrId + ",\"quantite\":1}]"
                + "}";

        String resp = mockMvc.perform(post("/api/restaurant/commandes")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long commandeId = readJsonLong(resp, "$.commandeId");

        // Verifications repos
        tx.execute(s -> {
            TenantContext.set(hotelMrId);
            try {
                Commande commande = commandeRepository.findById(commandeId).orElseThrow();
                assertThat(commande.getModeReglement()).isEqualTo(ModeReglementCommande.REPORTE_CHAMBRE);
                assertThat(commande.getReservationId()).isEqualTo(reservationId);
                assertThat(commande.getFactureId()).isNull();
                // Pas de Facture creee
                assertThat(factureRepository.count()).isZero();
                return null;
            } finally {
                TenantContext.clear();
            }
        });
    }
}
