package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.EncaissementCommandeDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.client.ClientService;
import com.cityprojects.citybackend.service.inventory.CategorieProduitService;
import com.cityprojects.citybackend.service.inventory.ProduitService;
import com.cityprojects.citybackend.service.restaurant.ArticleMenuService;
import com.cityprojects.citybackend.service.restaurant.CategorieMenuService;
import com.cityprojects.citybackend.service.restaurant.CommandeService;
import com.cityprojects.citybackend.service.restaurant.RecetteArticleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link CommandeService} (Tour 24).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste une commande COMPTANT avec numero
 *       COMM-{annee}-MR-000001 + lignes snapshotees + recalc total.</li>
 *   <li>T2 : changeStatut() respecte les transitions valides
 *       BROUILLON -&gt; VALIDEE -&gt; EN_PREPARATION -&gt; PRETE -&gt; SERVIE.</li>
 *   <li>T3 : annuler() refuse une commande SERVIE (BusinessException
 *       error.commande.annulation.servieInterdite).</li>
 *   <li>T4 : encaisserComptant() cree une Facture EMISE + un Paiement VALIDE,
 *       met a jour commande.factureId et commande.montantPaye.</li>
 *   <li>T5 : create(REPORTE_CHAMBRE) avec reservation ARRIVEE valide ->
 *       commande.reservationId set, modeReglement=REPORTE_CHAMBRE,
 *       PAS de Facture immediate.</li>
 *   <li>T10 (Tour 25bis F9) : create(REPORTE_CHAMBRE) avec reservationId
 *       d'un autre tenant -&gt; ResourceNotFoundException.</li>
 *   <li>T11 (Tour 25bis F9) : create() avec ligne dont articleId d'un autre
 *       tenant -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CommandeServiceTests {

    @Autowired private CommandeService commandeService;
    @Autowired private ClientService clientService;
    @Autowired private CategorieMenuService categorieService;
    @Autowired private ArticleMenuService articleService;
    @Autowired private RecetteArticleService recetteService;
    @Autowired private CategorieProduitService categorieProduitService;
    @Autowired private ProduitService produitService;

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ProduitRepository produitRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private DBUser userGerant;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userGerant = userRepository.saveAndFlush(new DBUser(
                "gerant1", "gerant1@mr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Sidi", "Mohamed", mr, gerant));

        // SecurityContext peuple pour les services qui lisent currentUserId().
        UserPrincipal principal = UserPrincipal.create(userGerant,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
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

    /** Helper : seed un client + une categorie + 1 article a 1500 MRU dans le tenant courant. */
    private ArticleMenuDto seedCatalogue() {
        CategorieMenuDto cat = tx.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Plats", null, null, 0)));
        return tx.execute(s -> articleService.create(new ArticleMenuCreateDto(
                "PLT1", "Riz au poisson", null,
                cat.categorieId(), BigDecimal.valueOf(1500), null, Boolean.TRUE)));
    }

    private Long seedClient() {
        ClientDto client = tx.execute(s -> clientService.create(
                new ClientCreateDto("Sidi", "Mohamed", null, "+22245100200",
                        "sidi@example.mr", null, null, null, null, null, null, null)));
        return client.clientId();
    }

    @Test
    @DisplayName("T1 - create() persiste une commande COMPTANT avec numero COMM-{annee}-MR-000001 + lignes snapshotees")
    void shouldCreateCommandeComptant() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeCreateDto dto = new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, /* clientId */ null, /* reservationId */ null,
                "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(),
                        BigDecimal.valueOf(2), null, "sans coriandre")));

        CommandeDto created = tx.execute(s -> commandeService.create(dto));

        assertNotNull(created);
        assertNotNull(created.commandeId());
        assertTrue(created.numeroCommande().matches("COMM-\\d{4}-MR-000001"),
                "Format attendu COMM-YYYY-MR-000001 mais : " + created.numeroCommande());
        assertEquals(ModeReglementCommande.COMPTANT, created.modeReglement());
        assertEquals(StatutCommande.BROUILLON, created.statut());
        assertEquals("MRU", created.devise());
        assertNull(created.reservationId());
        assertNull(created.factureId());

        // Total = 2 * 1500 = 3000 (pas de TVA POS : ttc == ht)
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("3000.00")));
        assertEquals(0, created.montantHt().compareTo(new BigDecimal("3000.00")));

        // 1 ligne snapshotee
        assertEquals(1, created.lignes().size());
        var l = created.lignes().get(0);
        assertEquals("Riz au poisson", l.libelle()); // snapshot du nom catalogue
        assertEquals(0, l.prixUnitaire().compareTo(new BigDecimal("1500.00")));
        assertEquals(0, l.montant().compareTo(new BigDecimal("3000.00")));
        assertEquals("sans coriandre", l.notesCuisine());

        // Verification hotel_id en base
        Long hotelIdInDb = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM restaurant.commandes WHERE commande_id = ?",
                Long.class, created.commandeId());
        assertEquals(hotelMrId, hotelIdInDb);
    }

    @Test
    @DisplayName("T2 - changeStatut() suit BROUILLON -> VALIDEE -> EN_PREPARATION -> PRETE -> SERVIE")
    void shouldFollowStatusTransitions() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        Long id = created.commandeId();

        CommandeDto valide = tx.execute(s -> commandeService.changeStatut(id, StatutCommande.VALIDEE));
        assertEquals(StatutCommande.VALIDEE, valide.statut());

        CommandeDto enPrep = tx.execute(s -> commandeService.changeStatut(id, StatutCommande.EN_PREPARATION));
        assertEquals(StatutCommande.EN_PREPARATION, enPrep.statut());

        CommandeDto prete = tx.execute(s -> commandeService.changeStatut(id, StatutCommande.PRETE));
        assertEquals(StatutCommande.PRETE, prete.statut());

        CommandeDto servie = tx.execute(s -> commandeService.changeStatut(id, StatutCommande.SERVIE));
        assertEquals(StatutCommande.SERVIE, servie.statut());

        // Transition interdite : SERVIE est terminal -> tout statut suivant doit echouer.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.changeStatut(id, StatutCommande.PRETE)));
        assertEquals("error.commande.statut.invalide", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - annuler() refuse une commande SERVIE")
    void shouldRejectAnnulationOnServie() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        Long id = created.commandeId();

        // Avance jusqu'a SERVIE
        tx.execute(s -> commandeService.changeStatut(id, StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(id, StatutCommande.EN_PREPARATION));
        tx.execute(s -> commandeService.changeStatut(id, StatutCommande.PRETE));
        tx.execute(s -> commandeService.changeStatut(id, StatutCommande.SERVIE));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.annuler(id, "test")));
        assertEquals("error.commande.annulation.servieInterdite", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - encaisserComptant() cree Facture EMISE + Paiement VALIDE + commande.factureId set")
    void shouldEncaisserComptant() {
        TenantContext.set(hotelMrId);
        Long clientId = seedClient();
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, clientId, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(),
                        BigDecimal.valueOf(3), null, null)))));
        BigDecimal total = created.montantTtc(); // 3 * 1500 = 4500
        assertEquals(0, total.compareTo(new BigDecimal("4500.00")));

        CommandeDto encaissee = tx.execute(s -> commandeService.encaisserComptant(
                created.commandeId(),
                new EncaissementCommandeDto(ModePaiement.BANKILY, total, "REF-BNK-001")));

        // commande.factureId pointe sur la facture creee
        assertNotNull(encaissee.factureId(), "factureId doit etre renseigne apres encaissement");
        assertEquals(0, encaissee.montantPaye().compareTo(total));

        // Facture cree, statut EMISE (puis sera passee a PAYEE par PaiementService.affecter)
        var facture = tx.execute(s -> {
            TenantContext.set(hotelMrId);
            try {
                return factureRepository.findById(encaissee.factureId()).orElseThrow();
            } finally {
                TenantContext.clear();
            }
        });
        assertNotNull(facture);
        // L'affectation directe via paiementService.create -> affecter passe la facture en PAYEE
        // (le total = montantTtc, donc statut == PAYEE).
        assertEquals(StatutFacture.PAYEE, facture.getStatut());
        assertEquals(0, facture.getMontantTtc().compareTo(total));
        assertEquals(clientId, facture.getClientId());

        // Paiement cree, statut VALIDE
        TenantContext.set(hotelMrId);
        try {
            var paiements = paiementRepository.findAll();
            assertEquals(1, paiements.size());
            var p = paiements.get(0);
            assertEquals(StatutPaiement.VALIDE, p.getStatut());
            assertEquals(ModePaiement.BANKILY, p.getModePaiement());
            assertEquals(0, p.getMontantTotal().compareTo(total));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T5 - create(REPORTE_CHAMBRE) avec reservation ARRIVEE -> reservationId set, pas de Facture")
    void shouldCreateReporteChambre() {
        TenantContext.set(hotelMrId);
        Long clientId = seedClient();

        // Cree directement une reservation ARRIVEE en JPA pour eviter le flux complet
        // chambre/check-in (couvert par ReservationFlowE2EIT). Pattern identique
        // a ReservationControllerIT.createReservationInTenant.
        Long reservationId;
        try {
            TenantContext.set(hotelMrId);
            reservationId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-MR-999999");
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

        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.REPORTE_CHAMBRE, clientId, reservationId, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(),
                        BigDecimal.ONE, null, null)))));

        assertEquals(ModeReglementCommande.REPORTE_CHAMBRE, created.modeReglement());
        assertEquals(reservationId, created.reservationId());
        assertNull(created.factureId(), "Pas de Facture creee tant qu'on n'a pas check-out");
        assertEquals(StatutCommande.BROUILLON, created.statut());

        // Verifie qu'aucune facture n'a ete persistee.
        TenantContext.set(hotelMrId);
        try {
            assertEquals(0, factureRepository.count(),
                    "Aucune facture ne doit exister pour une commande REPORTE_CHAMBRE");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T6 - create(COMPTANT) avec reservationId fourni -> BusinessException")
    void shouldRejectComptantWithReservation() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        // reservationId factice (n'a pas besoin d'exister, le service rejette des
        // la coherence mode/reservation)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.create(new CommandeCreateDto(
                        ModeReglementCommande.COMPTANT, null, 999L, "MRU", null,
                        List.of(new LigneCommandeCreateDto(art.articleId(),
                                BigDecimal.ONE, null, null))))));
        assertEquals("error.commande.reservation.interditComptant", ex.getMessage());
    }

    /** Helper Tour 25 : seed 1 produit (stock initial via SQL direct). */
    private Long seedProduitAvecStock(int stockInitial) {
        CategorieProduitDto catP = tx.execute(s -> categorieProduitService.create(
                new CategorieProduitCreateDto("PRD", "Produits", null)));
        ProduitDto prod = tx.execute(s -> produitService.create(new ProduitCreateDto(
                "RIZ001", "Riz", null, catP.categorieId(), "kg",
                BigDecimal.valueOf(500), 10, 5, null, Boolean.TRUE)));
        // Initialise le stock : on utilise un UPDATE direct (le service ProduitService
        // ne propose pas de setter direct ; en flow normal le stock vient d'un BC)
        TenantContext.set(hotelMrId);
        try {
            tx.execute(s -> {
                Produit p = produitRepository.findById(prod.produitId()).orElseThrow();
                p.setStockActuel(stockInitial);
                return produitRepository.save(p);
            });
        } finally {
            TenantContext.set(hotelMrId); // garde le contexte pour la suite
        }
        return prod.produitId();
    }

    @Test
    @DisplayName("T8 - PRETE -> SERVIE avec recette : BS auto cree + stock decremente")
    void shouldGenerateBsOnServeWhenRecipeDefined() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        Long produitId = seedProduitAvecStock(100);

        // Definit recette : 0.150 kg de riz par plat -> arrondi CEILING -> 1
        tx.execute(s -> recetteService.create(new RecetteArticleCreateDto(
                art.articleId(), produitId, new BigDecimal("0.1500"), "kg", null)));

        // Cree commande, 2 plats
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));

        // Avance jusqu'a SERVIE
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.EN_PREPARATION));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.PRETE));
        CommandeDto servie = tx.execute(s -> commandeService.changeStatut(
                created.commandeId(), StatutCommande.SERVIE));
        assertEquals(StatutCommande.SERVIE, servie.statut());

        // Verifie BS cree (1 ligne, qte CEILING(0.150 * 2) = CEILING(0.300) = 1)
        Integer countBs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.bons_sortie", Integer.class);
        assertEquals(1, countBs);

        // Verifie stock decremente : 100 - 1 = 99
        TenantContext.set(hotelMrId);
        Integer stockApres = tx.execute(s -> produitRepository.findById(produitId)
                .orElseThrow().getStockActuel());
        assertEquals(99, stockApres);
    }

    @Test
    @DisplayName("T9 - PRETE -> SERVIE sans recette : transition OK, aucun BS cree")
    void shouldSkipBsOnServeWhenNoRecipeDefined() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        // Pas de recette pour cet article

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));

        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.EN_PREPARATION));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.PRETE));
        CommandeDto servie = tx.execute(s -> commandeService.changeStatut(
                created.commandeId(), StatutCommande.SERVIE));

        assertEquals(StatutCommande.SERVIE, servie.statut());

        // Aucun BS n'a du etre cree
        Integer countBs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.bons_sortie", Integer.class);
        assertEquals(0, countBs);
    }

    @Test
    @DisplayName("T7 - findById() depuis un autre tenant -> ResourceNotFoundException")
    void shouldNotFindCrossTenantCommande() {
        // hotel FR
        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        Long hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Cree une commande dans MR
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(),
                        BigDecimal.ONE, null, null)))));
        Long mrCommandeId = created.commandeId();
        TenantContext.clear();

        // Tente de la lire depuis FR
        TenantContext.set(hotelFrId);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> commandeService.findById(mrCommandeId)));
        assertEquals("error.commande.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T10 (Tour 25bis F9) - create(REPORTE_CHAMBRE) avec reservationId cross-tenant -> ResourceNotFoundException")
    void shouldRejectCreateWithCrossTenantReservation() {
        // hotel FR
        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        Long hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Seed une reservation ARRIVEE dans tenant FR
        Long reservationFrId;
        TenantContext.set(hotelFrId);
        try {
            // Un client minimum cote FR (sinon FK NULLable mais on prefere realiste)
            Long clientFrId = seedClient();
            reservationFrId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-FR-999998");
                r.setClientPrincipalId(clientFrId);
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

        // Tenant MR : tente de creer une commande REPORTE_CHAMBRE pointant sur
        // la reservation FR. Hibernate @TenantId filtre -> findById renvoie
        // Optional.empty -> 404.
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        Long crossReservationId = reservationFrId;
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> commandeService.create(new CommandeCreateDto(
                        ModeReglementCommande.REPORTE_CHAMBRE, null, crossReservationId, "MRU", null,
                        List.of(new LigneCommandeCreateDto(art.articleId(),
                                BigDecimal.ONE, null, null))))));
        assertEquals("error.reservation.notFound", ex.getMessage());
    }

    // ====================================================================
    // Tour 50 : nouveaux tests (findByReservation, findByClient, addLigne,
    // removeLigne, annulerLigne)
    // ====================================================================

    @Test
    @DisplayName("T50.1 - findByReservation() retourne les commandes REPORTE_CHAMBRE d'une reservation")
    void shouldFindByReservation() {
        TenantContext.set(hotelMrId);
        Long clientId = seedClient();

        // Cree une reservation ARRIVEE
        Long reservationId;
        try {
            TenantContext.set(hotelMrId);
            reservationId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-MR-555555");
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

        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        // Deux commandes REPORTE_CHAMBRE sur la meme reservation
        tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.REPORTE_CHAMBRE, clientId, reservationId, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.REPORTE_CHAMBRE, clientId, reservationId, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));

        List<CommandeDto> commandes = tx.execute(s -> commandeService.findByReservation(reservationId));
        assertEquals(2, commandes.size());
        assertTrue(commandes.stream().allMatch(c -> reservationId.equals(c.reservationId())));
        assertTrue(commandes.stream().allMatch(c -> c.modeReglement() == ModeReglementCommande.REPORTE_CHAMBRE));
    }

    @Test
    @DisplayName("T50.2 - findByReservation() depuis un autre tenant -> liste vide (Hibernate @TenantId filtre)")
    void shouldNotFindReservationCommandesFromOtherTenant() {
        // Hotel FR
        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        Long hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Setup reservation + commandes dans MR
        TenantContext.set(hotelMrId);
        Long clientId = seedClient();
        Long reservationId;
        try {
            reservationId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-MR-555556");
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

        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();
        tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.REPORTE_CHAMBRE, clientId, reservationId, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        TenantContext.clear();

        // Lecture depuis tenant FR : Hibernate filtre, liste vide.
        TenantContext.set(hotelFrId);
        List<CommandeDto> commandes = tx.execute(s -> commandeService.findByReservation(reservationId));
        assertEquals(0, commandes.size(),
                "Commandes du tenant MR ne doivent pas apparaitre depuis le tenant FR");
    }

    @Test
    @DisplayName("T50.3 - addLigne() ajoute une ligne sur une commande BROUILLON")
    void shouldAddLigneInBrouillon() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        assertEquals(1, created.lignes().size());
        assertEquals(0, created.montantTtc().compareTo(new BigDecimal("1500.00")));

        CommandeDto updated = tx.execute(s -> commandeService.addLigne(
                created.commandeId(),
                new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, "extra epice")));

        assertEquals(2, updated.lignes().size());
        // Total = 1500 + 2*1500 = 4500
        assertEquals(0, updated.montantTtc().compareTo(new BigDecimal("4500.00")));
    }

    @Test
    @DisplayName("T50.4 - addLigne() refuse une commande VALIDEE -> BusinessException")
    void shouldRejectAddLigneOnValidee() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.addLigne(created.commandeId(),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null))));
        assertEquals("error.commande.ligne.ajoutInterdit", ex.getMessage());
    }

    @Test
    @DisplayName("T50.5 - removeLigne() supprime une ligne en BROUILLON et recalcule")
    void shouldRemoveLigneInBrouillon() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        // Commande avec 2 lignes
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));
        assertEquals(2, created.lignes().size());

        Long ligneIdARetirer = created.lignes().get(1).ligneId();
        CommandeDto updated = tx.execute(s -> commandeService.removeLigne(
                created.commandeId(), ligneIdARetirer));

        assertEquals(1, updated.lignes().size());
        // Total = 1 * 1500 = 1500
        assertEquals(0, updated.montantTtc().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("T50.6 - removeLigne() refuse en VALIDEE -> error.commande.ligne.suppressionInterdite")
    void shouldRejectRemoveLigneAfterEnvoiCuisine() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));

        Long ligneId = created.lignes().get(0).ligneId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.removeLigne(created.commandeId(), ligneId)));
        assertEquals("error.commande.ligne.suppressionInterdite", ex.getMessage());
    }

    @Test
    @DisplayName("T50.7 - removeLigne() refuse la suppression de la derniere ligne")
    void shouldRejectRemoveLastLigne() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));

        Long ligneId = created.lignes().get(0).ligneId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.removeLigne(created.commandeId(), ligneId)));
        assertEquals("error.commande.ligne.derniereInterdite", ex.getMessage());
    }

    @Test
    @DisplayName("T50.8 - annulerLigne() avec motif sur commande VALIDEE supprime la ligne et recalcule")
    void shouldAnnulerLigneWithMotifAfterEnvoi() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.EN_PREPARATION));

        Long ligneId = created.lignes().get(1).ligneId();
        CommandeDto updated = tx.execute(s -> commandeService.annulerLigne(
                created.commandeId(), ligneId, "rupture stock ingredient"));

        assertEquals(1, updated.lignes().size());
        assertEquals(0, updated.montantTtc().compareTo(new BigDecimal("1500.00")));
        assertEquals(StatutCommande.EN_PREPARATION, updated.statut(),
                "Le statut commande ne doit pas changer");
    }

    @Test
    @DisplayName("T50.9 - annulerLigne() refuse motif vide")
    void shouldRejectAnnulerLigneWithoutMotif() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));

        Long ligneId = created.lignes().get(0).ligneId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.annulerLigne(created.commandeId(), ligneId, "  ")));
        assertEquals("error.ligneCommande.motif.required", ex.getMessage());
    }

    @Test
    @DisplayName("T50.10 - annulerLigne() refuse sur SERVIE -> error.commande.ligne.annulationInterdite")
    void shouldRejectAnnulerLigneOnServie() {
        TenantContext.set(hotelMrId);
        ArticleMenuDto art = seedCatalogue();

        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null),
                        new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(2), null, null)))));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.EN_PREPARATION));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.PRETE));
        tx.execute(s -> commandeService.changeStatut(created.commandeId(), StatutCommande.SERVIE));

        Long ligneId = created.lignes().get(0).ligneId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> commandeService.annulerLigne(
                        created.commandeId(), ligneId, "test")));
        assertEquals("error.commande.ligne.annulationInterdite", ex.getMessage());
    }

    @Test
    @DisplayName("T50.11 - findByClient() retourne les commandes du client triees desc + filtre tenant")
    void shouldFindByClient() {
        TenantContext.set(hotelMrId);
        Long clientId = seedClient();
        ArticleMenuDto art = seedCatalogue();

        tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, clientId, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.ONE, null, null)))));
        tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.COMPTANT, clientId, null, "MRU", null,
                List.of(new LigneCommandeCreateDto(art.articleId(), BigDecimal.valueOf(3), null, null)))));

        var page = tx.execute(s -> commandeService.findByClient(
                clientId, org.springframework.data.domain.PageRequest.of(0, 10)));
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(c -> clientId.equals(c.clientId())));
    }

    @Test
    @DisplayName("T11 (Tour 25bis F9) - create() avec articleId cross-tenant -> ResourceNotFoundException")
    void shouldRejectCreateWithCrossTenantArticle() {
        // hotel FR
        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        Long hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Seed un article cote FR
        TenantContext.set(hotelFrId);
        ArticleMenuDto artFr = seedCatalogue();
        Long articleFrId = artFr.articleId();
        TenantContext.clear();

        // Tenant MR : tente de creer une commande pointant sur l'article FR.
        // Hibernate @TenantId filtre -> creerLigne lance findById qui renvoie
        // Optional.empty -> 404.
        TenantContext.set(hotelMrId);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> commandeService.create(new CommandeCreateDto(
                        ModeReglementCommande.COMPTANT, null, null, "MRU", null,
                        List.of(new LigneCommandeCreateDto(articleFrId,
                                BigDecimal.ONE, null, null))))));
        assertEquals("error.articleMenu.notFound", ex.getMessage());
    }
}
