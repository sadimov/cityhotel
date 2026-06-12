package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.EtatJourneeHoteliere;
import com.cityprojects.citybackend.entity.hebergement.JourneeHoteliere;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.hebergement.JourneeHoteliereRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire de {@link HotelDayService} (cycle de vie de la journée
 * hôtelière).
 *
 * <p>On vérifie :
 * <ul>
 *   <li>Bootstrap : pas de journée → currentDay() en crée une OUVERTE.</li>
 *   <li>startClosure : OUVERTE → CLOTURE_EN_COURS + verrouille le registry.</li>
 *   <li>startClosure double : second appel rejeté
 *       ({@code error.nightAudit.alreadyRunning}).</li>
 *   <li>completeClosure : CLOTURE_EN_COURS → CLOTUREE + ouvre J+1.</li>
 *   <li>abortClosure : CLOTURE_EN_COURS → OUVERTE + dévérouille le registry.</li>
 *   <li>Multi-tenant : journée d'un hôtel invisible depuis un autre.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class HotelDayServiceTests {

    @Autowired
    private HotelDayService hotelDayService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JourneeHoteliereRepository journeeRepository;

    @Autowired
    private NightAuditLockRegistry lockRegistry;

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
        cleanAll();

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
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM hebergement.journee_hoteliere");
        jdbcTemplate.update("DELETE FROM core.hotels");
        if (lockRegistry != null) {
            lockRegistry.snapshot().forEach(lockRegistry::unlock);
        }
    }

    @Test
    @DisplayName("Bootstrap : aucune journée existante → currentDay() crée une OUVERTE")
    void bootstrap_createsOpenDay() {
        TenantContext.set(hotelMrId);
        try {
            JourneeHoteliere day = transactionTemplate.execute(s -> hotelDayService.currentDay());
            assertNotNull(day);
            assertNotNull(day.getId());
            assertEquals(EtatJourneeHoteliere.OUVERTE, day.getEtat());
            assertEquals(hotelMrId, day.getHotelId());
            assertEquals(LocalDate.now(), day.getDateHotel(),
                    "La date hôtelière initiale = LocalDate.now() (timezone locale OK pour test)");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("currentDay() idempotent : 2 appels → même journée")
    void currentDay_idempotent() {
        TenantContext.set(hotelMrId);
        try {
            JourneeHoteliere d1 = transactionTemplate.execute(s -> hotelDayService.currentDay());
            JourneeHoteliere d2 = transactionTemplate.execute(s -> hotelDayService.currentDay());
            assertEquals(d1.getId(), d2.getId(), "Pas de doublon : même ligne renvoyée");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("startClosure : OUVERTE → CLOTURE_EN_COURS + registry verrouillé")
    void startClosure_locksRegistry() {
        TenantContext.set(hotelMrId);
        try {
            assertFalse(lockRegistry.isLocked(hotelMrId), "Pré-condition : pas verrouillé");

            JourneeHoteliere day = transactionTemplate.execute(s -> hotelDayService.startClosure(42L));

            assertEquals(EtatJourneeHoteliere.CLOTURE_EN_COURS, day.getEtat());
            assertEquals(42L, day.getClosedByUserId());
            assertTrue(lockRegistry.isLocked(hotelMrId), "Registry doit être verrouillé après startClosure");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("startClosure double : second appel → BusinessException alreadyRunning")
    void startClosure_doubleCall_rejected() {
        TenantContext.set(hotelMrId);
        try {
            transactionTemplate.execute(s -> hotelDayService.startClosure(42L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> transactionTemplate.execute(s -> hotelDayService.startClosure(42L)));
            assertEquals("error.nightAudit.alreadyRunning", ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("completeClosure : CLOTURE_EN_COURS → CLOTUREE + ouvre J+1 + dévérouille")
    void completeClosure_opensNextDay() {
        TenantContext.set(hotelMrId);
        try {
            JourneeHoteliere day = transactionTemplate.execute(s -> hotelDayService.startClosure(42L));
            LocalDate dateCloturee = day.getDateHotel();

            JourneeHoteliere closed = transactionTemplate.execute(s -> hotelDayService.completeClosure());
            assertEquals(EtatJourneeHoteliere.CLOTUREE, closed.getEtat());
            assertNotNull(closed.getClosedAt());
            assertFalse(lockRegistry.isLocked(hotelMrId), "Registry libéré après completeClosure");

            // J+1 doit exister et être OUVERTE
            JourneeHoteliere next = transactionTemplate.execute(s ->
                    journeeRepository.findByDateHotel(dateCloturee.plusDays(1)).orElseThrow());
            assertEquals(EtatJourneeHoteliere.OUVERTE, next.getEtat());
            assertEquals(dateCloturee.plusDays(1), next.getDateHotel());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("abortClosure : CLOTURE_EN_COURS → OUVERTE + dévérouille (rollback)")
    void abortClosure_rollsBack() {
        TenantContext.set(hotelMrId);
        try {
            JourneeHoteliere day = transactionTemplate.execute(s -> hotelDayService.startClosure(42L));
            LocalDate dateInit = day.getDateHotel();

            JourneeHoteliere reopened = transactionTemplate.execute(s -> hotelDayService.abortClosure());
            assertEquals(EtatJourneeHoteliere.OUVERTE, reopened.getEtat());
            assertEquals(dateInit, reopened.getDateHotel(), "Même date : pas de bascule J+1");
            assertNull(reopened.getClosedByUserId(), "closed_by_user_id remis à null");
            assertFalse(lockRegistry.isLocked(hotelMrId), "Registry libéré après abort");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Multi-tenant : journée d'un hôtel invisible depuis l'autre")
    void multiTenant_isolation() {
        // Crée une journée pour MR
        TenantContext.set(hotelMrId);
        Long mrDayId;
        try {
            mrDayId = transactionTemplate.execute(s -> hotelDayService.currentDay()).getId();
        } finally {
            TenantContext.clear();
        }

        // FR voit sa propre journée (différente de celle de MR)
        TenantContext.set(hotelFrId);
        try {
            JourneeHoteliere frDay = transactionTemplate.execute(s -> hotelDayService.currentDay());
            assertEquals(hotelFrId, frDay.getHotelId(), "FR voit son propre tenant");
            assertEquals(EtatJourneeHoteliere.OUVERTE, frDay.getEtat());
            // mr != fr (id différents)
            assertEquals(false, mrDayId.equals(frDay.getId()), "IDs distincts entre tenants");
        } finally {
            TenantContext.clear();
        }
    }
}
