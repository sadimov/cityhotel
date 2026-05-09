package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.inventory.BonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionBonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionLigneDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.hamcrest.Matchers;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link BonCommandeService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() genere un numero BC-{annee}-MR-000001 + lignes + total calcule.</li>
 *   <li>T2 : transition de statut BROUILLON -&gt; ENVOYE -&gt; CONFIRME.</li>
 *   <li>T3 : reception complete passe statut a RECU_COMPLET et incremente le stock du produit.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class BonCommandeServiceTests {

    @Autowired
    private BonCommandeService bonCommandeService;

    @Autowired
    private FournisseurService fournisseurService;

    @Autowired
    private CategorieProduitService categorieService;

    @Autowired
    private ProduitService produitService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private DBUser userMr;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        currentYear = LocalDate.now().getYear();

        // Cleanup ordonne
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Role magasin = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));

        userMr = new DBUser("magasin1", "magasin1@h1.test", "$2a$12$placeholder",
                "Sidi", "Cheikh", mr, magasin);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticate() {
        UserPrincipal principal = UserPrincipal.create(userMr, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_MAGASIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Seed integer : un fournisseur + une categorie + un produit. */
    private record SeedFixtures(Long fournisseurId, Long categorieId, Long produitId) {
    }

    private SeedFixtures seedFixtures() {
        FournisseurDto fournisseur = transactionTemplate.execute(s -> fournisseurService.create(
                new FournisseurCreateDto("ACME Distribution", "M. Salem",
                        "+22244111111", "contact@acme.mr", null, "Nouakchott", "Mauritanie", null)));

        CategorieProduitDto categorie = transactionTemplate.execute(s -> categorieService.create(
                new CategorieProduitCreateDto("FB", "Food & Beverage", null)));

        ProduitDto produit = transactionTemplate.execute(s -> produitService.create(
                new ProduitCreateDto("EAU500", "Eau minerale 500ml", null,
                        categorie.categorieId(), "bouteille", BigDecimal.valueOf(20),
                        50, 20, fournisseur.fournisseurId(), Boolean.FALSE)));

        return new SeedFixtures(fournisseur.fournisseurId(), categorie.categorieId(),
                produit.produitId());
    }

    @Test
    @DisplayName("T1 - create() genere numero BC-{annee}-MR-000001 + lignes + total calcule")
    void shouldCreateBonCommande() {
        TenantContext.set(hotelMrId);
        authenticate();

        SeedFixtures s = seedFixtures();
        BonCommandeCreateDto dto = new BonCommandeCreateDto(
                s.fournisseurId(),
                LocalDate.now().plusDays(7),
                "Reapprovisionnement mensuel",
                List.of(new LigneBonCommandeCreateDto(s.produitId(), 100, BigDecimal.valueOf(15))));

        BonCommandeDto created = transactionTemplate.execute(t -> bonCommandeService.create(dto));

        assertNotNull(created);
        assertNotNull(created.bonCommandeId());
        assertEquals(String.format("BC-%d-MR-000001", currentYear), created.numeroBc(),
                "Le numero doit suivre BC-{annee}-{codePays}-{6 chiffres}");
        assertEquals(StatutBonCommande.BROUILLON, created.statut());
        assertEquals(s.fournisseurId(), created.fournisseurId());
        assertEquals(0, created.montantTotal().compareTo(BigDecimal.valueOf(1500.00)),
                "100 * 15.00 = 1500.00");
        assertNotNull(created.lignes());
        assertEquals(1, created.lignes().size());
        assertEquals(100, created.lignes().get(0).quantiteCommandee());
        assertEquals(0, created.lignes().get(0).quantiteRecue());
    }

    @Test
    @DisplayName("T2 - transition de statut BROUILLON -> ENVOYE -> CONFIRME via changerStatut()")
    void shouldChangerStatut() {
        TenantContext.set(hotelMrId);
        authenticate();

        SeedFixtures s = seedFixtures();
        BonCommandeDto created = transactionTemplate.execute(t -> bonCommandeService.create(
                new BonCommandeCreateDto(s.fournisseurId(), null, null,
                        List.of(new LigneBonCommandeCreateDto(s.produitId(), 50, BigDecimal.valueOf(10))))));
        Long bcId = created.bonCommandeId();

        BonCommandeDto envoye = transactionTemplate.execute(t ->
                bonCommandeService.changerStatut(bcId, StatutBonCommande.ENVOYE));
        assertEquals(StatutBonCommande.ENVOYE, envoye.statut());

        BonCommandeDto confirme = transactionTemplate.execute(t ->
                bonCommandeService.changerStatut(bcId, StatutBonCommande.CONFIRME));
        assertEquals(StatutBonCommande.CONFIRME, confirme.statut());
    }

    @Test
    @DisplayName("T3 - receptionner() complete passe statut a RECU_COMPLET et incremente stockActuel")
    void shouldReceptionnerEtIncrementerStock() {
        TenantContext.set(hotelMrId);
        authenticate();

        SeedFixtures s = seedFixtures();

        // Stock initial du produit
        ProduitDto produitAvant = transactionTemplate.execute(t -> produitService.findById(s.produitId()));
        assertEquals(0, produitAvant.stockActuel());

        // Cree un BC, le confirme, puis receptionne en totalite
        BonCommandeDto bc = transactionTemplate.execute(t -> bonCommandeService.create(
                new BonCommandeCreateDto(s.fournisseurId(), null, null,
                        List.of(new LigneBonCommandeCreateDto(s.produitId(), 100, BigDecimal.valueOf(15))))));
        Long bcId = bc.bonCommandeId();
        Long ligneId = bc.lignes().get(0).ligneId();

        transactionTemplate.execute(t -> bonCommandeService.changerStatut(bcId, StatutBonCommande.ENVOYE));
        transactionTemplate.execute(t -> bonCommandeService.changerStatut(bcId, StatutBonCommande.CONFIRME));

        // Reception complete (100 sur 100)
        ReceptionBonCommandeDto reception = new ReceptionBonCommandeDto(
                List.of(new ReceptionLigneDto(ligneId, 100)));
        BonCommandeDto recu = transactionTemplate.execute(t ->
                bonCommandeService.receptionner(bcId, reception));

        assertEquals(StatutBonCommande.RECU_COMPLET, recu.statut(),
                "Toutes les lignes servies -> RECU_COMPLET");
        assertEquals(100, recu.lignes().get(0).quantiteRecue());
        assertNotNull(recu.dateLivraisonReelle());

        // Le stock du produit doit etre incremente
        ProduitDto produitApres = transactionTemplate.execute(t -> produitService.findById(s.produitId()));
        assertEquals(100, produitApres.stockActuel(), "stockActuel = 0 + 100 apres reception");

        // Un MouvementStock ENTREE doit avoir ete cree
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.mouvements_stock WHERE produit_id = ? AND type_mouvement = 'ENTREE'",
                Integer.class, s.produitId());
        assertTrue(count >= 1, "Au moins un MouvementStock ENTREE genere");

        // Et la reference document doit pointer sur le numero du BC
        String reference = jdbcTemplate.queryForObject(
                "SELECT reference_document FROM inventory.mouvements_stock WHERE produit_id = ? AND type_mouvement = 'ENTREE'",
                String.class, s.produitId());
        assertThat(reference, Matchers.startsWith("BC-"));
    }
}
