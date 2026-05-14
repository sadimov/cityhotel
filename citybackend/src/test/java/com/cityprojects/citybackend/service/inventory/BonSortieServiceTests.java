package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.inventory.BonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire (H2) du {@link BonSortieService} - focus Tour 51bis :
 * transitions de statut + annulation avec motif obligatoire.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : valider() BROUILLON -&gt; VALIDE.</li>
 *   <li>T2 : annuler() BROUILLON avec motif renseigne -&gt; ANNULE + motif persiste.</li>
 *   <li>T3 : annuler() refuse motif blank ({@code error.bonSortie.motif.required}).</li>
 *   <li>T4 : annuler() refuse un BS deja LIVRE
 *       ({@code error.bonSortie.annulation.statutInvalide}).</li>
 *   <li>T5 : livrer() decremente le stock et passe le BS a LIVRE.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class BonSortieServiceTests {

    @Autowired
    private BonSortieService bonSortieService;

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

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
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

        userMr = new DBUser("bsTester", "bsTester@h1.test", "$2a$12$placeholder",
                "Test", "User", mr, magasin);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMr = userRepository.saveAndFlush(userMr);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
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

    /** Cree un produit avec stock initial et renvoie son id. */
    private Long seedProduitWithStock(int stockInitial) {
        CategorieProduitDto cat = transactionTemplate.execute(s -> categorieService.create(
                new CategorieProduitCreateDto("FB", "Food & Beverage", null)));
        ProduitDto produit = transactionTemplate.execute(s -> produitService.create(
                new ProduitCreateDto("EAU500", "Eau minerale 500ml", null,
                        cat.categorieId(), "bouteille", BigDecimal.valueOf(20),
                        50, 20, null, Boolean.FALSE)));
        // Ajustement manuel du stock (les ProduitService.create cree avec stock 0)
        jdbcTemplate.update(
                "UPDATE inventory.produits SET stock_actuel = ? WHERE produit_id = ?",
                stockInitial, produit.produitId());
        return produit.produitId();
    }

    private BonSortieDto seedBs(Long produitId, int quantite) {
        return transactionTemplate.execute(t -> bonSortieService.create(
                new BonSortieCreateDto("Cuisine", "Service midi",
                        List.of(new LigneBonSortieCreateDto(produitId, quantite, null)))));
    }

    @Test
    @DisplayName("T1 - valider() : BROUILLON -> VALIDE")
    void shouldValider() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long produitId = seedProduitWithStock(100);
        BonSortieDto bs = seedBs(produitId, 10);
        assertEquals(StatutBonSortie.BROUILLON, bs.statut());

        BonSortieDto valide = transactionTemplate.execute(t ->
                bonSortieService.valider(bs.bonSortieId()));
        assertEquals(StatutBonSortie.VALIDE, valide.statut());
    }

    @Test
    @DisplayName("T2 - annuler(motif) : BROUILLON -> ANNULE + motif persiste")
    void shouldAnnulerWithMotif() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long produitId = seedProduitWithStock(100);
        BonSortieDto bs = seedBs(produitId, 5);

        BonSortieDto annule = transactionTemplate.execute(t ->
                bonSortieService.annuler(bs.bonSortieId(), "Erreur de saisie"));

        assertNotNull(annule);
        assertEquals(StatutBonSortie.ANNULE, annule.statut());
        assertEquals("Erreur de saisie", annule.motifAnnulation(),
                "Le motif doit etre persiste pour audit");

        // Verification SQL directe (le motif est en BDD)
        String motifEnBase = jdbcTemplate.queryForObject(
                "SELECT motif_annulation FROM inventory.bons_sortie WHERE bon_sortie_id = ?",
                String.class, bs.bonSortieId());
        assertEquals("Erreur de saisie", motifEnBase);
    }

    @Test
    @DisplayName("T3 - annuler(motif) refuse un motif blank")
    void shouldRejectBlankMotif() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long produitId = seedProduitWithStock(100);
        BonSortieDto bs = seedBs(produitId, 5);

        // null
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> bonSortieService.annuler(bs.bonSortieId(), null)));
        assertEquals("error.bonSortie.motif.required", ex1.getMessage());

        // chaine vide / blank
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t -> bonSortieService.annuler(bs.bonSortieId(), "   ")));
        assertEquals("error.bonSortie.motif.required", ex2.getMessage());

        // Le BS reste BROUILLON
        BonSortieDto reloaded = transactionTemplate.execute(t ->
                bonSortieService.findById(bs.bonSortieId()));
        assertEquals(StatutBonSortie.BROUILLON, reloaded.statut());
        assertNull(reloaded.motifAnnulation());
    }

    @Test
    @DisplayName("T4 - annuler(motif) refuse un BS deja LIVRE")
    void shouldRejectAnnulerWhenLivre() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long produitId = seedProduitWithStock(100);
        BonSortieDto bs = seedBs(produitId, 10);

        // workflow complet : valider + livrer pour le mettre en LIVRE
        transactionTemplate.execute(t -> bonSortieService.valider(bs.bonSortieId()));
        BonSortieDto livre = transactionTemplate.execute(t ->
                bonSortieService.livrer(bs.bonSortieId()));
        assertEquals(StatutBonSortie.LIVRE, livre.statut());

        // Annulation refusee
        BusinessException ex = assertThrows(BusinessException.class, () ->
                transactionTemplate.execute(t ->
                        bonSortieService.annuler(bs.bonSortieId(), "Trop tard")));
        assertEquals("error.bonSortie.annulation.statutInvalide", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - livrer() decremente le stock et passe le BS a LIVRE")
    void shouldLivrerEtDecrementerStock() {
        TenantContext.set(hotelMrId);
        authenticate();

        Long produitId = seedProduitWithStock(100);
        BonSortieDto bs = seedBs(produitId, 30);

        transactionTemplate.execute(t -> bonSortieService.valider(bs.bonSortieId()));
        BonSortieDto livre = transactionTemplate.execute(t ->
                bonSortieService.livrer(bs.bonSortieId()));

        assertEquals(StatutBonSortie.LIVRE, livre.statut());

        // Verifie le decrement de stock
        Integer stockApres = jdbcTemplate.queryForObject(
                "SELECT stock_actuel FROM inventory.produits WHERE produit_id = ?",
                Integer.class, produitId);
        assertEquals(70, stockApres, "100 - 30 = 70");

        // Verifie qu'un MouvementStock SORTIE a ete cree
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.mouvements_stock "
                        + "WHERE produit_id = ? AND type_mouvement = 'SORTIE'",
                Integer.class, produitId);
        assertEquals(1, count);
    }
}
