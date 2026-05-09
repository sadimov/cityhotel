package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.entity.core.Hotel;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link CategorieMenuService} (Tour 23).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste une categorie avec hotelId du TenantContext + actif=true.</li>
 *   <li>T2 : findById() depuis un autre tenant -&gt; ResourceNotFoundException
 *       (Hibernate filtre via @TenantId).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class CategorieMenuServiceTests {

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

        // Cleanup ordonne (FK : articles -> categories -> hotels)
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

    @Test
    @DisplayName("T1 - create() persiste une categorie avec hotelId resolu via TenantContext et actif=true")
    void shouldCreateCategorie() {
        TenantContext.set(hotelMrId);
        CategorieMenuCreateDto dto = new CategorieMenuCreateDto(
                "Boissons", "Sodas, jus, eaux minerales", null, 1);

        CategorieMenuDto created = transactionTemplate.execute(s -> categorieService.create(dto));

        assertNotNull(created);
        assertNotNull(created.categorieId(), "id genere par la base");
        assertEquals("Boissons", created.nom());
        assertEquals("Sodas, jus, eaux minerales", created.description());
        assertEquals(1, created.ordre());
        assertTrue(Boolean.TRUE.equals(created.actif()));
        // hotelId NON expose par le DTO (pattern projet).

        // Verification du hotel_id reellement persiste en base (isolation)
        Long persistedHotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM restaurant.categories_menus WHERE categorie_id = ?",
                Long.class, created.categorieId());
        assertEquals(hotelMrId, persistedHotelId,
                "hotel_id en base doit correspondre au TenantContext, pas a un parametre DTO");
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantCategorie() {
        // Seed dans hotel MR
        TenantContext.set(hotelMrId);
        CategorieMenuDto created = transactionTemplate.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Plats", "Plats principaux", null, 2)));
        TenantContext.clear();

        // Lecture depuis hotel FR -> filtre Hibernate -> 404
        TenantContext.set(hotelFrId);
        Long foreignId = created.categorieId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> categorieService.findById(foreignId)));
        assertEquals("error.categorieMenu.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - findAllActive() ne retourne que les categories du tenant courant, triees par ordre asc")
    void shouldFindAllActiveOnlyCurrentTenant() {
        // 2 categories dans MR (ordres 2 et 1)
        TenantContext.set(hotelMrId);
        transactionTemplate.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Plats", null, null, 2)));
        transactionTemplate.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Boissons", null, null, 1)));
        TenantContext.clear();

        // 1 categorie dans FR
        TenantContext.set(hotelFrId);
        transactionTemplate.execute(s -> categorieService.create(
                new CategorieMenuCreateDto("Desserts", null, null, 3)));

        // Lecture cote FR : doit voir uniquement Desserts
        java.util.List<CategorieMenuDto> frList = transactionTemplate.execute(
                s -> categorieService.findAllActive());
        assertEquals(1, frList.size(), "Le tenant FR ne doit voir que ses propres categories");
        assertEquals("Desserts", frList.get(0).nom());
        TenantContext.clear();

        // Lecture cote MR : doit voir 2 categories triees par ordre asc (Boissons, Plats)
        TenantContext.set(hotelMrId);
        java.util.List<CategorieMenuDto> mrList = transactionTemplate.execute(
                s -> categorieService.findAllActive());
        assertEquals(2, mrList.size(), "Le tenant MR doit voir ses 2 categories");
        assertEquals("Boissons", mrList.get(0).nom(), "Tri par ordre ASC : Boissons (1) avant Plats (2)");
        assertEquals("Plats", mrList.get(1).nom());
    }
}
