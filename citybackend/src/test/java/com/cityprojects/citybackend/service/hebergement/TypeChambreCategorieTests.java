package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.CategorieEspace;
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

/**
 * Tests Surefire (H2) du Tour 49 : ajout {@link CategorieEspace} sur
 * {@link com.cityprojects.citybackend.entity.hebergement.TypeChambre}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() sans categorie -&gt; defaut CHAMBRE applique cote service.</li>
 *   <li>T2 : create() avec categorie SALLE -&gt; persistee telle quelle.</li>
 *   <li>T3 : multi-tenant - type SALLE cree pour hotel MR invisible depuis FR.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TypeChambreCategorieTests {

    @Autowired
    private TypeChambreService typeChambreService;

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

        // Cleanup ordonne (FK : types_chambres -> hotels)
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.hotels");

        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    @Test
    @DisplayName("T1 - create() sans categorie -> defaut CHAMBRE")
    void shouldDefaultToCategorieChambreWhenNotProvided() {
        TenantContext.set(hotelMrId);

        TypeChambreCreateDto dto = new TypeChambreCreateDto(
                "STD", "Standard", "Description",
                new BigDecimal("20.00"), 2, 2,
                new BigDecimal("80.00"),
                null /* categorie absente */);

        TypeChambreDto created = transactionTemplate.execute(s -> typeChambreService.create(dto));

        assertNotNull(created);
        assertNotNull(created.typeId());
        assertEquals(CategorieEspace.CHAMBRE, created.categorie(),
                "Defaut CHAMBRE applique cote service si categorie absente");
    }

    @Test
    @DisplayName("T2 - create() avec categorie SALLE -> persistee telle quelle")
    void shouldPersistCategorieSalleWhenProvided() {
        TenantContext.set(hotelMrId);

        TypeChambreCreateDto dto = new TypeChambreCreateDto(
                "CONF", "Salle de conference", "Grande salle 50 personnes",
                new BigDecimal("80.00"), 1, 50,
                new BigDecimal("500.00"),
                CategorieEspace.SALLE);

        TypeChambreDto created = transactionTemplate.execute(s -> typeChambreService.create(dto));

        assertNotNull(created);
        assertNotNull(created.typeId());
        assertEquals(CategorieEspace.SALLE, created.categorie(),
                "Categorie SALLE doit etre persistee a l'identique");
        assertEquals("CONF", created.typeCode());

        // Verification BDD brute (le CHECK constraint accepte SALLE)
        String categorieDb = jdbcTemplate.queryForObject(
                "SELECT categorie FROM hebergement.types_chambres WHERE type_id = ?",
                String.class, created.typeId());
        assertEquals("SALLE", categorieDb, "Colonne categorie BDD = 'SALLE'");
    }

    @Test
    @DisplayName("T3 - Multi-tenant : type SALLE cree pour MR invisible depuis FR")
    void shouldIsolateCategorieSalleAcrossTenants() {
        // Cree un type SALLE cote MR
        TenantContext.set(hotelMrId);
        TypeChambreCreateDto dto = new TypeChambreCreateDto(
                "CONF", "Salle reunion", null,
                null, 1, 20,
                new BigDecimal("200.00"),
                CategorieEspace.SALLE);
        TypeChambreDto createdMr = transactionTemplate.execute(s -> typeChambreService.create(dto));
        TenantContext.clear();

        // Tente lecture depuis hotel FR -> isolation Hibernate @TenantId
        TenantContext.set(hotelFrId);
        Long foreignTypeId = createdMr.typeId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> typeChambreService.findById(foreignTypeId)));
        assertEquals("error.typeChambre.notFound", ex.getMessage(),
                "Tenant FR ne doit pas voir le type SALLE cree par MR");
    }
}
