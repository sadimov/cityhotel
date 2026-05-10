package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.admin.HotelAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelUpdateAdminDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link HotelAdminService}.
 *
 * <p>Couverture :
 * <ol>
 *   <li>T1 : create() persiste l'hotel avec defauts (devise=MRU, codePays=MR,
 *       fuseauHoraire=Africa/Nouakchott, actif=true).</li>
 *   <li>T2 : create() refuse un hotelCode deja existant (BusinessException).</li>
 *   <li>T3 : update() applique les champs non-null (semantique PATCH).</li>
 *   <li>T4 : desactiver() positionne actif=false ; idempotent.</li>
 *   <li>T5 : reactiver() positionne actif=true ; idempotent.</li>
 *   <li>T6 : findById() sur id inexistant -&gt; ResourceNotFoundException.</li>
 * </ol>
 *
 * <p>Note : pas de {@code @Transactional} sur la classe (cf. ClientServiceTests).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class HotelAdminServiceTests {

    @Autowired
    private HotelAdminService hotelAdminService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        // Cleanup ordonne (FK aval potentielles : dbusers.hotel_id par ex.)
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
    }

    @Test
    @DisplayName("T1 - create() persiste avec defauts (MRU, MR, Africa/Nouakchott, actif=true)")
    void shouldCreateHotelWithDefaults() {
        HotelCreateAdminDto dto = new HotelCreateAdminDto(
                "MR001", "Hotel Mauritanie", null, null, null, null, null,
                null, null, null, null, null, null);

        HotelAdminDto created = transactionTemplate.execute(s -> hotelAdminService.create(dto));

        assertNotNull(created);
        assertNotNull(created.hotelId());
        assertEquals("MR001", created.hotelCode());
        assertEquals("Hotel Mauritanie", created.hotelNom());
        assertEquals("MRU", created.devise());
        assertEquals("MR", created.codePays());
        assertEquals("Africa/Nouakchott", created.fuseauHoraire());
        assertTrue(Boolean.TRUE.equals(created.actif()));
    }

    @Test
    @DisplayName("T2 - create() refuse un hotelCode deja existant -> BusinessException")
    void shouldRejectDuplicateHotelCode() {
        transactionTemplate.execute(s -> hotelAdminService.create(
                new HotelCreateAdminDto("DUP1", "Hotel Un", null, null, null, null, null,
                        null, null, null, null, null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> hotelAdminService.create(
                        new HotelCreateAdminDto("DUP1", "Hotel Deux", null, null, null, null, null,
                                null, null, null, null, null, null))));
        assertEquals("error.hotel.code.alreadyExists", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - update() applique uniquement les champs non-null (semantique PATCH)")
    void shouldUpdateHotelPartial() {
        HotelAdminDto created = transactionTemplate.execute(s -> hotelAdminService.create(
                new HotelCreateAdminDto("UP1", "Nom Initial", "Adresse Initiale", null, null,
                        null, null, null, null, null, null, null, null)));

        HotelUpdateAdminDto patch = new HotelUpdateAdminDto(
                "Nouveau Nom", null, "+22245111000", null, null, null,
                null, null, null, null, null, null);

        HotelAdminDto updated = transactionTemplate.execute(s ->
                hotelAdminService.update(created.hotelId(), patch));

        assertEquals("Nouveau Nom", updated.hotelNom());
        // adresse non touchee (patch null sur ce champ)
        assertEquals("Adresse Initiale", updated.hotelAdresse());
        assertEquals("+22245111000", updated.hotelTel());
        // hotelCode reste immuable
        assertEquals("UP1", updated.hotelCode());
    }

    @Test
    @DisplayName("T4 - desactiver() positionne actif=false ; second appel idempotent")
    void shouldDeactivateAndBeIdempotent() {
        HotelAdminDto created = transactionTemplate.execute(s -> hotelAdminService.create(
                new HotelCreateAdminDto("DEAC", "A Desactiver", null, null, null, null, null,
                        null, null, null, null, null, null)));

        transactionTemplate.executeWithoutResult(s -> hotelAdminService.desactiver(created.hotelId()));
        HotelAdminDto reloaded = transactionTemplate.execute(s -> hotelAdminService.findById(created.hotelId()));
        assertFalse(Boolean.TRUE.equals(reloaded.actif()));

        // Idempotent : pas d'exception sur 2e appel
        transactionTemplate.executeWithoutResult(s -> hotelAdminService.desactiver(created.hotelId()));
        HotelAdminDto reloaded2 = transactionTemplate.execute(s -> hotelAdminService.findById(created.hotelId()));
        assertFalse(Boolean.TRUE.equals(reloaded2.actif()));
    }

    @Test
    @DisplayName("T5 - reactiver() positionne actif=true apres desactiver ; idempotent")
    void shouldReactivateAndBeIdempotent() {
        HotelAdminDto created = transactionTemplate.execute(s -> hotelAdminService.create(
                new HotelCreateAdminDto("REAC", "A Reactiver", null, null, null, null, null,
                        null, null, null, null, null, null)));
        transactionTemplate.executeWithoutResult(s -> hotelAdminService.desactiver(created.hotelId()));
        transactionTemplate.executeWithoutResult(s -> hotelAdminService.reactiver(created.hotelId()));

        HotelAdminDto reloaded = transactionTemplate.execute(s -> hotelAdminService.findById(created.hotelId()));
        assertTrue(Boolean.TRUE.equals(reloaded.actif()));

        // Idempotent : pas d'exception sur 2e reactivation
        transactionTemplate.executeWithoutResult(s -> hotelAdminService.reactiver(created.hotelId()));
    }

    @Test
    @DisplayName("T6 - findById() sur id inexistant -> ResourceNotFoundException")
    void shouldThrowOnFindByIdMissing() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> hotelAdminService.findById(9_999_999L)));
        assertEquals("error.hotel.notFound", ex.getMessage());
    }
}
