package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2) du {@link PersonnelService} (Tour 27).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste avec hotelId resolu via TenantContext + actif=true.</li>
 *   <li>T2 : findById() depuis un autre tenant -&gt; ResourceNotFoundException
 *       (Hibernate filtre via @TenantId).</li>
 *   <li>T3 : create() avec numeroEmploye en doublon dans le tenant -&gt; BusinessException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PersonnelServiceTests {

    @Autowired
    private PersonnelService personnelService;

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

        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
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
        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    @Test
    @DisplayName("T1 - create() persiste un Personnel avec hotelId resolu via TenantContext et actif=true")
    void shouldCreatePersonnel() {
        TenantContext.set(hotelMrId);
        PersonnelCreateDto dto = new PersonnelCreateDto(
                "MEN001", "Ahmed", "Hassan",
                "+22245678910", "ahmed@hotel.test",
                LocalDate.of(2024, 1, 15),
                "[\"chambres\",\"salles\"]");

        PersonnelDto created = transactionTemplate.execute(s -> personnelService.create(dto));

        assertNotNull(created);
        assertNotNull(created.personnelId(), "id genere par la base");
        assertEquals("MEN001", created.numeroEmploye());
        assertEquals("Ahmed", created.prenom());
        assertEquals("Hassan", created.nom());
        assertEquals("Ahmed Hassan", created.nomComplet());
        assertTrue(Boolean.TRUE.equals(created.actif()));

        // hotel_id en base (pas dans le DTO)
        Long persistedHotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM menage.personnel WHERE personnel_id = ?",
                Long.class, created.personnelId());
        assertEquals(hotelMrId, persistedHotelId,
                "hotel_id en base doit correspondre au TenantContext, pas a un parametre DTO");
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation Hibernate)")
    void shouldNotFindCrossTenantPersonnel() {
        TenantContext.set(hotelMrId);
        PersonnelDto created = transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN002", "Fatima", "Bint Mohamed",
                        null, null, null, null)));
        TenantContext.clear();

        TenantContext.set(hotelFrId);
        Long foreignId = created.personnelId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> personnelService.findById(foreignId)));
        assertEquals("error.personnel.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - create() avec numeroEmploye en doublon dans le meme tenant -> BusinessException")
    void shouldRejectDuplicateNumeroInSameTenant() {
        TenantContext.set(hotelMrId);
        transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN100", "Aly", "Sow", null, null, null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> personnelService.create(
                        new PersonnelCreateDto("MEN100", "Other", "Person", null, null, null, null))));
        assertEquals("error.personnel.numeroEmploye.duplicate", ex.getMessage());

        // Le meme numero MEN100 doit etre acceptable dans un autre tenant (unicite par hotel)
        TenantContext.clear();
        TenantContext.set(hotelFrId);
        PersonnelDto inFr = transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN100", "Pierre", "Dupont", null, null, null, null)));
        assertNotNull(inFr.personnelId(), "MEN100 doit etre acceptable dans hotel FR (unicite par hotel)");
    }

    /**
     * Tour 30 etape 2 (FIX 🔴) : update() doit refuser un email deja porte par
     * un autre membre du personnel du meme tenant. Avant le fix, seul le
     * numeroEmploye etait controle a l'update -> un agent pouvait "voler"
     * l'email d'un collegue par simple PUT.
     */
    @Test
    @DisplayName("T4 - Etape 2 : update() avec email deja utilise par un autre personnel -> BusinessException")
    void shouldRejectUpdateWithDuplicateEmail() {
        TenantContext.set(hotelMrId);

        PersonnelDto a = transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN200", "Anna", "Sy", null,
                        "anna@hotel.test", null, null)));

        PersonnelDto b = transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN201", "Brahim", "Ndiaye", null,
                        "brahim@hotel.test", null, null)));

        // B tente de prendre l'email de A (case-insensitive)
        Long bId = b.personnelId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> personnelService.update(bId,
                        new PersonnelCreateDto("MEN201", "Brahim", "Ndiaye", null,
                                "ANNA@hotel.test", null, null))));
        assertEquals("error.personnel.email.duplicate", ex.getMessage());

        // B garde son email d'origine
        PersonnelDto bReloaded = transactionTemplate.execute(s -> personnelService.findById(bId));
        assertEquals("brahim@hotel.test", bReloaded.email());

        // Mise a jour de B vers son propre email (case differente) -> doit passer
        PersonnelDto bSelfUpdate = transactionTemplate.execute(s -> personnelService.update(bId,
                new PersonnelCreateDto("MEN201", "Brahim", "Ndiaye", null,
                        "BRAHIM@hotel.test", null, null)));
        assertEquals("BRAHIM@hotel.test", bSelfUpdate.email(),
                "Email de B doit pouvoir etre mis a jour vers la meme valeur (case differente)");

        // Confirme que A est intact
        PersonnelDto aReloaded = transactionTemplate.execute(s -> personnelService.findById(a.personnelId()));
        assertEquals("anna@hotel.test", aReloaded.email());
    }

    /**
     * Tour 30 etape 2 IMPORTANT 2 : trim defensif sur numeroEmploye et email
     * pour eviter "MEN300" vs "MEN300 " comme deux entrees distinctes.
     */
    @Test
    @DisplayName("T5 - Etape 2 : create() avec numeroEmploye trimable en doublon -> BusinessException")
    void shouldRejectDuplicateAfterTrim() {
        TenantContext.set(hotelMrId);
        transactionTemplate.execute(s -> personnelService.create(
                new PersonnelCreateDto("MEN300", "Charlie", "Diop", null, null, null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> personnelService.create(
                        new PersonnelCreateDto("  MEN300  ", "Other", "Person", null, null, null, null))));
        assertEquals("error.personnel.numeroEmploye.duplicate", ex.getMessage(),
                "Trim defensif : MEN300 et '  MEN300  ' sont la meme valeur logique");
    }
}
