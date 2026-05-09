package com.cityprojects.citybackend.e2e;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
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
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.client.ClientService;
import com.cityprojects.citybackend.service.finance.FactureService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test E2E (Failsafe) du flux complet "checkout avec commandes reportees" (Tour 25).
 *
 * <h3>Flux teste</h3>
 * <ol>
 *   <li>Seed : hotel + user + client + chambre + reservation ARRIVEE + 1 nuitee
 *       CONSOMMEE.</li>
 *   <li>Seed : article menu + recette (1 ingredient produit inventory + stock=100).</li>
 *   <li>Cree 1 commande POS REPORTE_CHAMBRE liee a la reservation, 1 ligne.</li>
 *   <li>Avance la commande BROUILLON -&gt; VALIDEE -&gt; EN_PREPARATION -&gt;
 *       PRETE -&gt; SERVIE. Verifie que stock a baisse (BS auto Tour 25).</li>
 *   <li>{@code factureService.fromReservation(reservationId)} : verifie que la
 *       facture inclut nuitee(s) ET ligne(s) de la commande reportee.</li>
 *   <li>Verifie que {@code commande.factureId == facture.factureId} et
 *       {@code reservation.factureId == facture.factureId}.</li>
 * </ol>
 *
 * <h3>Pas de @Transactional</h3>
 * <p>Pattern identique aux autres E2E : cleanup explicite en
 * {@link #setUp()} / {@link #tearDown()}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CheckoutWithReportedCommandesE2EIT {

    @Autowired private CommandeService commandeService;
    @Autowired private FactureService factureService;
    @Autowired private RecetteArticleService recetteService;
    @Autowired private CategorieMenuService categorieMenuService;
    @Autowired private ArticleMenuService articleService;
    @Autowired private CategorieProduitService categorieProduitService;
    @Autowired private ProduitService produitService;
    @Autowired private ClientService clientService;

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private NuiteeRepository nuiteeRepository;
    @Autowired private CommandeRepository commandeRepository;
    @Autowired private FactureRepository factureRepository;
    @Autowired private LigneFactureRepository ligneFactureRepository;
    @Autowired private ProduitRepository produitRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private DBUser userGerant;
    private Long hotelMrId;
    private Long clientId;
    private Long reservationId;
    private Long articleId;
    private Long produitId;

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

        // SecurityContext peuple
        UserPrincipal principal = UserPrincipal.create(userGerant,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // Client + Reservation ARRIVEE + 1 Nuitee CONSOMMEE
        try {
            TenantContext.set(hotelMrId);
            ClientDto client = tx.execute(s -> clientService.create(
                    new ClientCreateDto("Mariam", "Sow", null, "+22245100100",
                            "msow@example.mr", null, null, null, null, null, null, null)));
            clientId = client.clientId();

            reservationId = tx.execute(s -> {
                Reservation r = new Reservation();
                r.setNumeroReservation("RES-2026-MR-000001");
                r.setClientPrincipalId(clientId);
                r.setDateArrivee(LocalDate.now().minusDays(1));
                r.setDateDepart(LocalDate.now().plusDays(1));
                r.setNbAdultes(1);
                r.setNbEnfants(0);
                r.setStatut(StatutReservation.ARRIVEE);
                r.setReductionPourcentage(BigDecimal.ZERO);
                r.setMontantTotal(BigDecimal.ZERO);
                r.setUserId(userGerant.getUserId());
                return reservationRepository.saveAndFlush(r).getReservationId();
            });

            // 1 nuitee CONSOMMEE (sans chambre reelle - on simule au plus court).
            // Le test ne passe pas par le check-in service complet (couvert par
            // ReservationFlowE2EIT), il insere directement la nuitee.
            tx.execute(s -> {
                Nuitee n = new Nuitee();
                n.setReservationId(reservationId);
                n.setChambreId(99999L); // chambre fictive non FK-strict (H2 OK)
                n.setDateNuit(LocalDate.now().minusDays(1));
                n.setPrixNuit(new BigDecimal("100.00"));
                n.setStatut(StatutNuitee.CONSOMMEE);
                return nuiteeRepository.saveAndFlush(n);
            });
        } finally {
            TenantContext.clear();
        }

        // Catalogue restaurant + recette + produit
        try {
            TenantContext.set(hotelMrId);
            CategorieMenuDto cat = tx.execute(s -> categorieMenuService.create(
                    new CategorieMenuCreateDto("Plats", null, null, 0)));
            ArticleMenuDto art = tx.execute(s -> articleService.create(new ArticleMenuCreateDto(
                    "PLT1", "Riz au poisson", null,
                    cat.categorieId(), BigDecimal.valueOf(1500), null, Boolean.TRUE)));
            articleId = art.articleId();

            CategorieProduitDto catP = tx.execute(s -> categorieProduitService.create(
                    new CategorieProduitCreateDto("PRD", "Produits", null)));
            ProduitDto prod = tx.execute(s -> produitService.create(new ProduitCreateDto(
                    "RIZ001", "Riz", null, catP.categorieId(), "kg",
                    BigDecimal.valueOf(500), 10, 5, null, Boolean.TRUE)));
            produitId = prod.produitId();

            // Stock initial 100 (UPDATE direct car le service ne propose pas de setter)
            tx.execute(s -> {
                Produit p = produitRepository.findById(produitId).orElseThrow();
                p.setStockActuel(100);
                return produitRepository.save(p);
            });

            // Recette : 0.150 kg de riz par plat
            tx.execute(s -> recetteService.create(new RecetteArticleCreateDto(
                    articleId, produitId, new BigDecimal("0.1500"), "kg", null)));
        } finally {
            TenantContext.clear();
        }
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

    @Test
    @DisplayName("T1 - Checkout avec commande reportee : facture inclut nuitee + commande, BS auto cree, stock decremente")
    void shouldIncludeReportedCommandesInCheckoutInvoice() {
        TenantContext.set(hotelMrId);

        // 1) Cree la commande POS REPORTE_CHAMBRE liee a la reservation, 2 plats.
        CommandeDto created = tx.execute(s -> commandeService.create(new CommandeCreateDto(
                ModeReglementCommande.REPORTE_CHAMBRE, clientId, reservationId, "MRU", null,
                List.of(new LigneCommandeCreateDto(articleId,
                        BigDecimal.valueOf(2), null, null)))));
        Long commandeId = created.commandeId();
        assertThat(created.factureId()).isNull();
        assertThat(created.montantTtc()).isEqualByComparingTo(new BigDecimal("3000.00")); // 2 * 1500

        // 2) Avance commande -> SERVIE (genere BS auto)
        tx.execute(s -> commandeService.changeStatut(commandeId, StatutCommande.VALIDEE));
        tx.execute(s -> commandeService.changeStatut(commandeId, StatutCommande.EN_PREPARATION));
        tx.execute(s -> commandeService.changeStatut(commandeId, StatutCommande.PRETE));
        tx.execute(s -> commandeService.changeStatut(commandeId, StatutCommande.SERVIE));

        // Verifie stock decremente : CEILING(0.150 * 2) = CEILING(0.300) = 1, donc 100 - 1 = 99
        TenantContext.set(hotelMrId);
        Integer stockApres = tx.execute(s -> produitRepository.findById(produitId)
                .orElseThrow().getStockActuel());
        assertThat(stockApres).isEqualTo(99);

        // 3) Genere la facture sejour : doit inclure nuitees + commandes reportees
        FactureDto factureDto = tx.execute(s -> factureService.fromReservation(reservationId));
        Long factureId = factureDto.factureId();
        assertThat(factureId).isPositive();

        // 4) Verifications via repos
        tx.execute(s -> {
            TenantContext.set(hotelMrId);
            try {
                Facture facture = factureRepository.findById(factureId).orElseThrow();
                assertThat(facture.getStatut()).isEqualTo(StatutFacture.EMISE);
                // Total = 100 (nuitee) + 3000 (2 plats) = 3100
                assertThat(facture.getMontantTtc()).isEqualByComparingTo(new BigDecimal("3100.00"));
                assertThat(facture.getReservationId()).isEqualTo(reservationId);
                assertThat(facture.getClientId()).isEqualTo(clientId);

                // Commande mise a jour : factureId pointe sur la facture
                Commande cmd = commandeRepository.findById(commandeId).orElseThrow();
                assertThat(cmd.getFactureId()).isEqualTo(factureId);

                // Reservation mise a jour : factureId pointe sur la facture
                Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                assertThat(reservation.getFactureId()).isEqualTo(factureId);

                // Lignes facture : 1 NUITEE + 1 COMMANDE
                List<LigneFacture> lignes = ligneFactureRepository
                        .findByFactureIdOrderByLigneFactureIdAsc(factureId);
                assertThat(lignes).hasSize(2);
                long nbNuitee = lignes.stream()
                        .filter(l -> l.getTypeLigne() == TypeLigneFacture.NUITEE).count();
                long nbCommande = lignes.stream()
                        .filter(l -> l.getTypeLigne() == TypeLigneFacture.COMMANDE).count();
                assertThat(nbNuitee).isEqualTo(1);
                assertThat(nbCommande).isEqualTo(1);

                // La ligne COMMANDE pointe bien sur la commande
                LigneFacture ligneCmd = lignes.stream()
                        .filter(l -> l.getTypeLigne() == TypeLigneFacture.COMMANDE)
                        .findFirst().orElseThrow();
                assertThat(ligneCmd.getCommandeId()).isEqualTo(commandeId);
                assertThat(ligneCmd.getMontantTtc()).isEqualByComparingTo(new BigDecimal("3000.00"));
                return null;
            } finally {
                TenantContext.clear();
            }
        });
    }
}
