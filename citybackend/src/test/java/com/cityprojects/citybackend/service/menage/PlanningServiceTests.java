package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.dto.menage.PlanningCreateDto;
import com.cityprojects.citybackend.dto.menage.PlanningDto;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire (H2) du {@link PlanningService} (Tour 31).
 *
 * <p>Cree au Tour 31 a la suite de l'audit multitenant-guardian (Tour 29) qui
 * a identifie l'absence de tests cross-tenant pour {@code PlanningService}.
 * La defense applicative est en place dans
 * {@link PlanningServiceImpl#validerCoherence} qui appelle
 * {@code personnelRepository.findById(...)} : Hibernate filtre via
 * {@code @TenantId} sur {@code Personnel}, donc un personnel d'un autre hotel
 * leve {@link ResourceNotFoundException}("error.personnel.notFound").</p>
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>P1 : create() en happy path pour le tenant courant (smoke test).</li>
 *   <li>P2 : create() avec personnelId d'un autre tenant -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PlanningServiceTests {

    @Autowired
    private PlanningService planningService;

    @Autowired
    private PersonnelService personnelService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long personnelMrId;
    private Long personnelFrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();

        // Cleanup ordonne : historique -> taches -> planning -> personnel
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

        // 1 personnel par hotel pour pouvoir creer un planning legitime cote
        // MR (smoke test) ET pouvoir tenter un appel cross-tenant cote FR
        // avec un personnelId qui n'existe que cote MR.
        try {
            TenantContext.set(hotelMrId);
            PersonnelDto pMr = tx.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MEN700", "Aly", "Sow",
                            null, null, null, null)));
            personnelMrId = pMr.personnelId();
        } finally {
            TenantContext.clear();
        }

        try {
            TenantContext.set(hotelFrId);
            PersonnelDto pFr = tx.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MEN701", "Marie", "Martin",
                            null, null, null, null)));
            personnelFrId = pFr.personnelId();
        } finally {
            TenantContext.clear();
        }
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

    /**
     * Smoke test : un planning cree pour un personnel du tenant courant doit
     * etre persiste avec disponible=true par defaut et hotel_id resolu via
     * TenantContext.
     */
    @Test
    @DisplayName("P1 - create() persiste un planning pour le personnel du tenant courant")
    void shouldCreatePlanningForCurrentTenant() {
        TenantContext.set(hotelMrId);

        PlanningCreateDto dto = new PlanningCreateDto(
                personnelMrId, LocalDate.now(),
                LocalTime.of(8, 0), LocalTime.of(16, 0),
                null, "Service du matin");

        PlanningDto created = tx.execute(s -> planningService.create(dto));

        assertNotNull(created);
        assertNotNull(created.planningId());
        assertEquals(personnelMrId, created.personnelId());
        assertEquals(LocalTime.of(8, 0), created.heureDebut());
        assertEquals(LocalTime.of(16, 0), created.heureFin());
        assertEquals(Boolean.TRUE, created.disponible(),
                "disponible doit valoir true par defaut quand le DTO est null");

        // hotel_id en base
        Long persistedHotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM menage.planning WHERE planning_id = ?",
                Long.class, created.planningId());
        assertEquals(hotelMrId, persistedHotelId);
    }

    /**
     * Tour 31 (audit Tour 29 multitenant-guardian) : depuis le tenant FR, un
     * appel a {@code create()} avec un {@code personnelId} appartenant au
     * tenant MR doit lever {@link ResourceNotFoundException}
     * ("error.personnel.notFound").
     *
     * <p>Couvre {@code PlanningServiceImpl#validerCoherence:130-131}. Hibernate
     * filtre {@code WHERE hotel_id = ?} via {@code @TenantId} sur
     * {@code Personnel}, donc {@code findById(personnelMrId)} sous
     * TenantContext = FR retourne {@code Optional.empty()}.</p>
     */
    @Test
    @DisplayName("P2 - Tour 31 : create() avec personnelId d'un autre tenant -> ResourceNotFoundException")
    void shouldRejectPlanningWithCrossTenantPersonnel() {
        TenantContext.set(hotelFrId);
        Long foreignPersonnelId = personnelMrId;

        PlanningCreateDto dto = new PlanningCreateDto(
                foreignPersonnelId, LocalDate.now(),
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                Boolean.TRUE, "tentative cross-tenant");

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> tx.execute(s -> planningService.create(dto)));
        assertEquals("error.personnel.notFound", ex.getMessage());
    }
}
