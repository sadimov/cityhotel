package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.dto.restaurant.LigneRecetteDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.inventory.CategorieProduitService;
import com.cityprojects.citybackend.service.inventory.ProduitService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link RecetteArticleService} (Tour 25).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste une recette avec hotelId du TenantContext +
 *       actif=true.</li>
 *   <li>T2 : findActiveByArticle() filtre actif/inactif, findAllByArticle()
 *       retourne tout.</li>
 *   <li>T3 (Tour 25bis F8) : create() avec articleId tenant A et produitId
 *       tenant B -&gt; ResourceNotFoundException (Hibernate @TenantId filtre).</li>
 *   <li>T4 (Tour 25bis F8) : setRecetteForArticle() avec articleId d'un autre
 *       tenant -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class RecetteArticleServiceTests {

    @Autowired private RecetteArticleService recetteService;
    @Autowired private CategorieMenuService categorieMenuService;
    @Autowired private ArticleMenuService articleService;
    @Autowired private CategorieProduitService categorieProduitService;
    @Autowired private ProduitService produitService;

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long hotelFrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        DBUser userGerant = userRepository.saveAndFlush(new DBUser(
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
        jdbcTemplate.update("DELETE FROM restaurant.recettes_articles");
        jdbcTemplate.update("DELETE FROM restaurant.lignes_commande");
        jdbcTemplate.update("DELETE FROM restaurant.commandes");
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
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /** Helper : seed 1 article + 1 produit dans le tenant courant. */
    private long[] seedArticleEtProduit() {
        CategorieMenuDto cat = tx.execute(s -> categorieMenuService.create(
                new CategorieMenuCreateDto("Plats", null, null, 0)));
        ArticleMenuDto art = tx.execute(s -> articleService.create(new ArticleMenuCreateDto(
                "PLT1", "Riz au poisson", null,
                cat.categorieId(), BigDecimal.valueOf(1500), null, Boolean.TRUE)));

        CategorieProduitDto catProd = tx.execute(s -> categorieProduitService.create(
                new CategorieProduitCreateDto("PRD", "Produits", null)));
        ProduitDto prod = tx.execute(s -> produitService.create(new ProduitCreateDto(
                "RIZ001", "Riz", null, catProd.categorieId(), "kg",
                BigDecimal.valueOf(500), 10, 5, null, Boolean.TRUE)));

        return new long[]{art.articleId(), prod.produitId()};
    }

    @Test
    @DisplayName("T1 - create() persiste une recette avec hotelId du TenantContext + actif=true")
    void shouldCreateRecette() {
        TenantContext.set(hotelMrId);
        long[] ids = seedArticleEtProduit();
        Long articleId = ids[0];
        Long produitId = ids[1];

        RecetteArticleDto dto = tx.execute(s -> recetteService.create(
                new RecetteArticleCreateDto(articleId, produitId,
                        new BigDecimal("0.1500"), "kg", "ingredient principal")));

        assertNotNull(dto);
        assertNotNull(dto.recetteId());
        assertEquals(articleId, dto.articleId());
        assertEquals(produitId, dto.produitId());
        assertEquals(0, dto.quantiteParUnite().compareTo(new BigDecimal("0.1500")));
        assertEquals("kg", dto.unite());
        assertEquals("ingredient principal", dto.note());
        assertTrue(dto.actif());

        // hotel_id en base
        Long hotelIdInDb = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM restaurant.recettes_articles WHERE recette_id = ?",
                Long.class, dto.recetteId());
        assertEquals(hotelMrId, hotelIdInDb);
    }

    @Test
    @DisplayName("T2 - findActiveByArticle() filtre les desactivees, findAllByArticle() les inclut")
    void shouldFilterActiveRecipes() {
        TenantContext.set(hotelMrId);
        long[] ids = seedArticleEtProduit();
        Long articleId = ids[0];
        Long produitId1 = ids[1];

        // Cree un 2e produit pour avoir 2 lignes de recette
        CategorieProduitDto catProd = tx.execute(s -> categorieProduitService.findAllActive().get(0));
        ProduitDto prod2 = tx.execute(s -> produitService.create(new ProduitCreateDto(
                "POI001", "Poisson", null, catProd.categorieId(), "kg",
                BigDecimal.valueOf(2000), 5, 2, null, Boolean.TRUE)));

        RecetteArticleDto r1 = tx.execute(s -> recetteService.create(
                new RecetteArticleCreateDto(articleId, produitId1,
                        new BigDecimal("0.1500"), "kg", null)));
        RecetteArticleDto r2 = tx.execute(s -> recetteService.create(
                new RecetteArticleCreateDto(articleId, prod2.produitId(),
                        new BigDecimal("0.2000"), "kg", null)));

        // Soft delete r2
        tx.execute(s -> {
            recetteService.delete(r2.recetteId());
            return null;
        });

        List<RecetteArticleDto> actives = recetteService.findActiveByArticle(articleId);
        List<RecetteArticleDto> all = recetteService.findAllByArticle(articleId);

        assertEquals(1, actives.size(), "Seul r1 doit rester actif");
        assertEquals(r1.recetteId(), actives.get(0).recetteId());
        assertEquals(2, all.size(), "Les 2 lignes doivent etre presentes (active + inactive)");
    }

    @Test
    @DisplayName("T3 (Tour 25bis F8) - create() articleId tenant A + produitId tenant B -> ResourceNotFoundException")
    void shouldRejectCreateCrossTenantArticleAndProduit() {
        // Tenant FR : seed un produit
        TenantContext.set(hotelFrId);
        long[] frIds = seedArticleEtProduit();
        Long produitFrId = frIds[1];
        TenantContext.clear();

        // Tenant MR : seed un article
        TenantContext.set(hotelMrId);
        long[] mrIds = seedArticleEtProduit();
        Long articleMrId = mrIds[0];

        // Tente de creer une recette MR avec un produit FR : Hibernate @TenantId
        // filtre -> ProduitRepository.findById renvoie Optional.empty -> 404.
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> recetteService.create(new RecetteArticleCreateDto(
                        articleMrId, produitFrId,
                        new BigDecimal("0.1500"), "kg", null))));
        assertEquals("error.produit.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T4 (Tour 25bis F8) - setRecetteForArticle() avec articleId cross-tenant -> ResourceNotFoundException")
    void shouldRejectSetRecetteCrossTenantArticle() {
        // Tenant MR : seed article + produit
        TenantContext.set(hotelMrId);
        long[] mrIds = seedArticleEtProduit();
        Long articleMrId = mrIds[0];
        Long produitMrId = mrIds[1];
        TenantContext.clear();

        // Tenant FR : tente de remplacer la recette d'un article du tenant MR.
        // Hibernate @TenantId filtre -> ArticleMenuRepository.findById renvoie
        // Optional.empty -> 404.
        TenantContext.set(hotelFrId);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> recetteService.setRecetteForArticle(
                        articleMrId,
                        List.of(new LigneRecetteDto(produitMrId,
                                new BigDecimal("0.1500"), "kg", null)))));
        assertEquals("error.articleMenu.notFound", ex.getMessage());
    }
}
