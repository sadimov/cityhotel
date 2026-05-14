package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link ServiceHotelierService} et de son
 * compagnon {@link TypeServiceHotelierService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() TypeServiceHotelier persiste avec hotelId du TenantContext.</li>
 *   <li>T2 : create() ServiceHotelier persiste avec FK type valide + actif=true.</li>
 *   <li>T3 : create() ServiceHotelier sur un type d'un autre tenant -&gt; 404.</li>
 *   <li>T4 : findById() depuis un autre tenant -&gt; ResourceNotFoundException.</li>
 *   <li>T5 : update() modifie prix/nom/description mais code immuable.</li>
 *   <li>T6 : deactivate() bascule actif=false.</li>
 *   <li>T7 : deactivate() du type refusee si services actifs lies.</li>
 *   <li>T8 : findAllActive() filtre par tenant courant uniquement.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceHotelierServiceTests {

    @Autowired
    private ServiceHotelierService serviceHotelierService;

    @Autowired
    private TypeServiceHotelierService typeServiceHotelierService;

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
        SecurityContextHolder.clearContext();

        jdbcTemplate.update("DELETE FROM inventory.services_hoteliers");
        jdbcTemplate.update("DELETE FROM inventory.types_services_hoteliers");
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
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM inventory.services_hoteliers");
        jdbcTemplate.update("DELETE FROM inventory.types_services_hoteliers");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    private TypeServiceHotelierDto seedType(String code, String nom) {
        return transactionTemplate.execute(s -> typeServiceHotelierService.create(
                new TypeServiceHotelierCreateDto(code, nom, null)));
    }

    private ServiceHotelierDto seedService(Long typeId, String code, String nom, BigDecimal prix) {
        return transactionTemplate.execute(s -> serviceHotelierService.create(
                new ServiceHotelierCreateDto(typeId, code, nom, null, prix, "prestation")));
    }

    @Test
    @DisplayName("T1 - create() TypeServiceHotelier persiste avec hotelId du TenantContext")
    void shouldCreateType() {
        TenantContext.set(hotelMrId);

        TypeServiceHotelierDto created = seedType("BIENETRE", "Bien-etre");

        assertNotNull(created);
        assertNotNull(created.typeServiceId());
        assertEquals("BIENETRE", created.code());
        assertEquals("Bien-etre", created.nom());
        assertTrue(Boolean.TRUE.equals(created.actif()));

        // Verif tenant en base
        Long hotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM inventory.types_services_hoteliers WHERE type_service_id = ?",
                Long.class, created.typeServiceId());
        assertEquals(hotelMrId, hotelId);
    }

    @Test
    @DisplayName("T2 - create() ServiceHotelier persiste avec FK type + actif=true")
    void shouldCreateService() {
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto type = seedType("BLANCHISS", "Blanchisserie");

        ServiceHotelierDto created = seedService(type.typeServiceId(), "BLANCH5K",
                "Blanchisserie 5 kg", BigDecimal.valueOf(5000));

        assertNotNull(created.serviceId());
        assertEquals("BLANCH5K", created.code());
        assertEquals("Blanchisserie 5 kg", created.nom());
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(created.prixUnitaire()));
        assertEquals("prestation", created.unite());
        assertTrue(Boolean.TRUE.equals(created.actif()));
        assertEquals(type.typeServiceId(), created.typeServiceId());
    }

    @Test
    @DisplayName("T3 - create() ServiceHotelier sur type d'un autre tenant -> 404")
    void shouldRejectServiceCreateOnForeignType() {
        // Type cree dans MR
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto typeMr = seedType("SPA", "Spa");
        TenantContext.clear();

        // Tentative de creation depuis FR avec l'id du type MR -> filtre @TenantId -> 404
        TenantContext.set(hotelFrId);
        Long foreignTypeId = typeMr.typeServiceId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> serviceHotelierService.create(
                        new ServiceHotelierCreateDto(foreignTypeId, "SPA60", "Spa 60min",
                                null, BigDecimal.valueOf(10000), "heure"))));
        assertEquals("error.typeServiceHotelier.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantService() {
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto type = seedType("TRANSP", "Transport");
        ServiceHotelierDto created = seedService(type.typeServiceId(), "TX-AERO",
                "Transfert aeroport", BigDecimal.valueOf(3000));
        TenantContext.clear();

        TenantContext.set(hotelFrId);
        Long foreignId = created.serviceId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> serviceHotelierService.findById(foreignId)));
        assertEquals("error.serviceHotelier.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - update() modifie prix/nom/description, code immuable")
    void shouldUpdateService() {
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto type = seedType("RESTAU", "Restauration");
        ServiceHotelierDto created = seedService(type.typeServiceId(), "PETITDEJ",
                "Petit dejeuner", BigDecimal.valueOf(1500));

        // Mise a jour : nouveau prix + nouveau nom
        ServiceHotelierCreateDto updateDto = new ServiceHotelierCreateDto(
                type.typeServiceId(), "PETITDEJ", "Petit dejeuner continental",
                "Inclus le matin", BigDecimal.valueOf(2000), "prestation");
        ServiceHotelierDto updated = transactionTemplate.execute(
                s -> serviceHotelierService.update(created.serviceId(), updateDto));

        assertEquals("PETITDEJ", updated.code(), "code immuable");
        assertEquals("Petit dejeuner continental", updated.nom());
        assertEquals("Inclus le matin", updated.description());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(updated.prixUnitaire()));
    }

    @Test
    @DisplayName("T6 - deactivate() bascule actif=false")
    void shouldDeactivateService() {
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto type = seedType("AUTRE", "Autre");
        ServiceHotelierDto created = seedService(type.typeServiceId(), "WIFI24",
                "Wifi premium 24h", BigDecimal.valueOf(500));

        transactionTemplate.executeWithoutResult(
                s -> serviceHotelierService.deactivate(created.serviceId()));

        ServiceHotelierDto reloaded = transactionTemplate.execute(
                s -> serviceHotelierService.findById(created.serviceId()));
        assertFalse(Boolean.TRUE.equals(reloaded.actif()));
    }

    @Test
    @DisplayName("T7 - deactivate() du type refusee si services actifs lies")
    void shouldRejectTypeDeactivateWithActiveServices() {
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto type = seedType("SPA", "Spa");
        seedService(type.typeServiceId(), "MASSAGE", "Massage 60min", BigDecimal.valueOf(8000));

        Long typeId = type.typeServiceId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.executeWithoutResult(
                        s -> typeServiceHotelierService.deactivate(typeId)));
        assertEquals("error.typeServiceHotelier.hasActiveServices", ex.getMessage());
    }

    @Test
    @DisplayName("T8 - findAllActive() filtre par tenant courant uniquement")
    void shouldListOnlyCurrentTenantServices() {
        // 2 services dans MR
        TenantContext.set(hotelMrId);
        TypeServiceHotelierDto typeMr = seedType("WELLNESS", "Bien-etre");
        seedService(typeMr.typeServiceId(), "SAUNA", "Sauna", BigDecimal.valueOf(2500));
        seedService(typeMr.typeServiceId(), "HAMMAM", "Hammam", BigDecimal.valueOf(2500));
        TenantContext.clear();

        // 1 service dans FR
        TenantContext.set(hotelFrId);
        TypeServiceHotelierDto typeFr = seedType("WELLNESS", "Bien-etre");
        seedService(typeFr.typeServiceId(), "JACUZZI", "Jacuzzi", BigDecimal.valueOf(3000));

        // Lecture depuis FR -> doit voir uniquement JACUZZI
        List<ServiceHotelierDto> activesFr = transactionTemplate.execute(
                s -> serviceHotelierService.findAllActive());
        assertNotNull(activesFr);
        assertEquals(1, activesFr.size());
        assertEquals("JACUZZI", activesFr.get(0).code());

        // Search avec pagination depuis MR -> 2 resultats
        TenantContext.clear();
        TenantContext.set(hotelMrId);
        Page<ServiceHotelierDto> pageMr = transactionTemplate.execute(
                s -> serviceHotelierService.search(null, null, PageRequest.of(0, 10)));
        assertNotNull(pageMr);
        assertEquals(2, pageMr.getTotalElements());
    }
}
