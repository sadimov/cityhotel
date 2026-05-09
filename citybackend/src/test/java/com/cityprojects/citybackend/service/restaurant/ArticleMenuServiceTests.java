package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link ArticleMenuService} (Tour 23).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste un article avec hotelId du TenantContext + statut=ACTIF par defaut.</li>
 *   <li>T2 : findById() depuis un autre tenant -&gt; ResourceNotFoundException
 *       (Hibernate filtre via @TenantId).</li>
 *   <li>T3 : changeStatut(ACTIF -&gt; RUPTURE) modifie le statut et persiste.</li>
 *   <li>T4 : create() avec code_article deja existant dans le tenant -&gt; BusinessException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleMenuServiceTests {

    @Autowired
    private ArticleMenuService articleService;

    @Autowired
    private CategorieMenuService categorieService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();

        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM restaurant.articles_menus");
        jdbcTemplate.update("DELETE FROM restaurant.categories_menus");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /** Helper pour seed une categorie dans le tenant courant. */
    private CategorieMenuDto seedCategorie(String nom) {
        return transactionTemplate.execute(s -> categorieService.create(
                new CategorieMenuCreateDto(nom, null, null, 0)));
    }

    @Test
    @DisplayName("T1 - create() persiste un article avec hotelId resolu via TenantContext et statut=ACTIF par defaut")
    void shouldCreateArticle() {
        TenantContext.set(hotelMrId);
        CategorieMenuDto cat = seedCategorie("Boissons");

        ArticleMenuCreateDto dto = new ArticleMenuCreateDto(
                "EAU500", "Eau minerale 500ml", "Bouteille 50 cl",
                cat.categorieId(), BigDecimal.valueOf(20), null, Boolean.TRUE);

        ArticleMenuDto created = transactionTemplate.execute(s -> articleService.create(dto));

        assertNotNull(created);
        assertNotNull(created.articleId(), "id genere par la base");
        assertEquals("EAU500", created.codeArticle());
        assertEquals("Eau minerale 500ml", created.nom());
        assertEquals(cat.categorieId(), created.categorieId());
        assertEquals(0, created.prix().compareTo(BigDecimal.valueOf(20)));
        assertEquals(StatutArticle.ACTIF, created.statut(), "Statut initial doit etre ACTIF");
        assertTrue(Boolean.TRUE.equals(created.actif()));
        assertTrue(Boolean.TRUE.equals(created.disponible()));

        // Verification hotel_id en base
        Long persistedHotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM restaurant.articles_menus WHERE article_id = ?",
                Long.class, created.articleId());
        assertEquals(hotelMrId, persistedHotelId);
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantArticle() {
        TenantContext.set(hotelMrId);
        CategorieMenuDto cat = seedCategorie("Plats");
        ArticleMenuDto created = transactionTemplate.execute(s -> articleService.create(
                new ArticleMenuCreateDto("PLT1", "Riz au poisson", null,
                        cat.categorieId(), BigDecimal.valueOf(150), null, null)));
        TenantContext.clear();

        TenantContext.set(hotelFrId);
        Long foreignId = created.articleId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> articleService.findById(foreignId)));
        assertEquals("error.articleMenu.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - changeStatut(ACTIF -> RUPTURE) met a jour le statut et persiste")
    void shouldChangeStatut() {
        TenantContext.set(hotelMrId);
        CategorieMenuDto cat = seedCategorie("Plats");
        ArticleMenuDto created = transactionTemplate.execute(s -> articleService.create(
                new ArticleMenuCreateDto("CCK1", "Couscous", null,
                        cat.categorieId(), BigDecimal.valueOf(200), null, null)));
        Long articleId = created.articleId();
        assertEquals(StatutArticle.ACTIF, created.statut());

        // Transition ACTIF -> RUPTURE
        ArticleMenuDto updated = transactionTemplate.execute(s ->
                articleService.changeStatut(articleId, StatutArticle.RUPTURE));
        assertEquals(StatutArticle.RUPTURE, updated.statut());

        // Verification en base
        String statutDb = jdbcTemplate.queryForObject(
                "SELECT statut FROM restaurant.articles_menus WHERE article_id = ?",
                String.class, articleId);
        assertEquals("RUPTURE", statutDb);
    }

    @Test
    @DisplayName("T4 - create() avec code_article deja utilise dans le tenant -> BusinessException")
    void shouldRejectDuplicateCodeArticleInSameTenant() {
        TenantContext.set(hotelMrId);
        CategorieMenuDto cat = seedCategorie("Boissons");

        transactionTemplate.execute(s -> articleService.create(new ArticleMenuCreateDto(
                "DUP1", "Article 1", null, cat.categorieId(),
                BigDecimal.valueOf(10), null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> articleService.create(new ArticleMenuCreateDto(
                        "DUP1", "Article 2", null, cat.categorieId(),
                        BigDecimal.valueOf(20), null, null))));
        assertEquals("error.articleMenu.code.alreadyExists", ex.getMessage());
    }
}
