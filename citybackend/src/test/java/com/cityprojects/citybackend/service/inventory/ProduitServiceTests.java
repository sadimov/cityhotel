package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.inventory.AjustementStockDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link ProduitService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste un produit avec hotelId du TenantContext + relation
 *       categorie OK + stockActuel initial = 0.</li>
 *   <li>T2 : findById() depuis un autre tenant -&gt; ResourceNotFoundException
 *       (Hibernate filtre via @TenantId).</li>
 *   <li>T3 : ajusterStock(AJUSTEMENT) incremente stockActuel + cree un MouvementStock.</li>
 * </ol>
 *
 * <p>Pattern Tour 8/11 : seed via repos JPA, transactions via TransactionTemplate
 * pour resoudre le tenant au bon moment.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProduitServiceTests {

    @Autowired
    private ProduitService produitService;

    @Autowired
    private CategorieProduitService categorieService;

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
    private Long hotelFrId;
    private DBUser userMr;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Cleanup ordonne (FK : produits -> categories/fournisseurs/hotels, mouvements -> produits)
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

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

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
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /** Helper pour seed une categorie dans le tenant courant. */
    private CategorieProduitDto seedCategorie(String code, String nom) {
        return transactionTemplate.execute(s -> categorieService.create(
                new CategorieProduitCreateDto(code, nom, null)));
    }

    /** Helper : alimenter le SecurityContext pour les ajustements (currentUserId()). */
    private void authenticate(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleCode())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - create() persiste un produit avec hotelId resolu via TenantContext et stockActuel=0")
    void shouldCreateProduit() {
        TenantContext.set(hotelMrId);

        CategorieProduitDto cat = seedCategorie("FB", "Food & Beverage");
        ProduitCreateDto dto = new ProduitCreateDto(
                "EAU500", "Eau minerale 500ml", "Bouteille 50 cl",
                cat.categorieId(), "bouteille", BigDecimal.valueOf(20),
                10, 5, null, Boolean.FALSE);

        ProduitDto created = transactionTemplate.execute(s -> produitService.create(dto));

        assertNotNull(created);
        assertNotNull(created.produitId(), "id genere par la base");
        assertEquals("EAU500", created.codeProduit());
        assertEquals("Eau minerale 500ml", created.nomProduit());
        assertEquals(cat.categorieId(), created.categorieId());
        assertEquals(0, created.stockActuel(), "stockActuel initial doit etre 0");
        assertTrue(Boolean.TRUE.equals(created.actif()));
        assertEquals("CRITIQUE", created.statutStock(), "stock=0 <= seuilCritique=5 -> CRITIQUE");
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantProduit() {
        // Seed dans hotel MR
        TenantContext.set(hotelMrId);
        CategorieProduitDto cat = seedCategorie("FB", "Food & Beverage");
        ProduitDto created = transactionTemplate.execute(s -> produitService.create(
                new ProduitCreateDto("PRD1", "Produit Test", null,
                        cat.categorieId(), "kg", BigDecimal.valueOf(100),
                        0, 0, null, Boolean.FALSE)));
        TenantContext.clear();

        // Lecture depuis hotel FR -> filtre Hibernate -> 404
        TenantContext.set(hotelFrId);
        Long foreignId = created.produitId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> produitService.findById(foreignId)));
        assertEquals("error.produit.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - ajusterStock(AJUSTEMENT) incremente stockActuel et cree un MouvementStock")
    void shouldAjusterStock() {
        TenantContext.set(hotelMrId);
        authenticate(userMr); // requis pour currentUserId() dans ajusterStock

        CategorieProduitDto cat = seedCategorie("FB", "Food & Beverage");
        ProduitDto created = transactionTemplate.execute(s -> produitService.create(
                new ProduitCreateDto("STK1", "Produit Stock", null,
                        cat.categorieId(), "kg", BigDecimal.valueOf(50),
                        0, 0, null, Boolean.FALSE)));
        Long produitId = created.produitId();
        assertEquals(0, created.stockActuel());

        // Ajustement +25
        AjustementStockDto ajust = new AjustementStockDto(
                TypeMouvementStock.AJUSTEMENT, 25, "Inventaire physique");
        ProduitDto adjusted = transactionTemplate.execute(s -> produitService.ajusterStock(produitId, ajust));

        assertEquals(25, adjusted.stockActuel(), "stockActuel doit etre 0 + 25 = 25");

        // Verification du mouvement persiste
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.mouvements_stock WHERE produit_id = ? AND type_mouvement = 'AJUSTEMENT'",
                Integer.class, produitId);
        assertEquals(1, count, "Un MouvementStock AJUSTEMENT doit etre cree");

        Integer stockApres = jdbcTemplate.queryForObject(
                "SELECT stock_apres FROM inventory.mouvements_stock WHERE produit_id = ?",
                Integer.class, produitId);
        assertEquals(25, stockApres, "stock_apres dans audit trail = 25");
    }
}
