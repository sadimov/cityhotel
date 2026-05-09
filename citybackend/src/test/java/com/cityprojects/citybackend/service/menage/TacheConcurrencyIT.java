package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'integration Failsafe (Tour 30 etape 3) : verifie que l'optimistic
 * locking ({@code @Version} sur {@code Tache}) protege les transitions
 * concurrentes.
 *
 * <h3>Scenario</h3>
 * <ol>
 *   <li>Cree une tache PLANIFIEE assignee a un personnel.</li>
 *   <li>Deux threads tentent simultanement {@code commencer()} sur la meme
 *       tache. Spring/Hibernate doit en serialiser un et faire echouer l'autre
 *       avec {@code OptimisticLockException} -&gt; traduit par
 *       {@code TacheServiceImpl} en {@code BusinessException("error.tache.concurrent.modification")}.</li>
 * </ol>
 *
 * <p>Pas de Testcontainers ici : H2 (profil "test") suffit pour reproduire le
 * comportement {@code @Version} de Hibernate (la regression visee est la
 * mutation perdue, pas une subtilite Postgres-specifique).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TacheConcurrencyIT {

    @Autowired private TacheService tacheService;
    @Autowired private PersonnelService personnelService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private TypeChambreRepository typeChambreRepository;
    @Autowired private ChambreRepository chambreRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long chambreMrId;
    private Long personnelMrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        cleanAll();

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = tx.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre chambre = tx.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("310");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId = chambre.getChambreId();

            PersonnelDto p = tx.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MENCONC", "Concurrent", "Worker",
                            null, null, null, null)));
            personnelMrId = p.personnelId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM menage.historique");
        jdbcTemplate.update("DELETE FROM menage.taches");
        jdbcTemplate.update("DELETE FROM menage.planning");
        jdbcTemplate.update("DELETE FROM menage.personnel");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.tarifs_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /**
     * Cree une tache PLANIFIEE et assigne le personnel, puis lance 2 threads
     * concurrents sur {@code terminer()} via {@code commencer + terminer}. Pour
     * faire le scenario simple, on lance 2 {@code commencer()} concurrents :
     * un seul peut reussir (PLANIFIEE -&gt; EN_COURS), l'autre soit voit deja
     * EN_COURS (rejet metier "invalidStatut"), soit perd la course optimistic
     * lock ("concurrent.modification"). Les deux issues sont valides : le test
     * assert qu'au moins UN echec est observe et qu'un seul commencer a
     * vraiment pose la transition.
     */
    @Test
    @DisplayName("TC1 - Etape 3 : 2 commencer() concurrents -> 1 succes, 1 echec (lock optimiste OU statut)")
    void shouldRejectConcurrentTransition() throws InterruptedException {
        TenantContext.set(hotelMrId);

        // Tache PLANIFIEE assignee
        TacheDto tache = tx.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, personnelMrId, null, null,
                        LocalDate.now(), null, null, null, null)));
        // assigner deja faite via le DTO (personnelId fourni a la creation)

        TenantContext.clear();

        Long taskId = tache.tacheId();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger statusRejectionCount = new AtomicInteger(0);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService exec = Executors.newFixedThreadPool(2);

        Runnable worker = () -> {
            try {
                TenantContext.set(hotelMrId);
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Latch start jamais leve");
                }
                tx.execute(s -> tacheService.commencer(taskId));
                successCount.incrementAndGet();
            } catch (BusinessException be) {
                if ("error.tache.concurrent.modification".equals(be.getMessage())) {
                    conflictCount.incrementAndGet();
                } else if ("error.tache.commencer.invalidStatut".equals(be.getMessage())) {
                    // Cas legitime : l'autre thread a fini en premier, celui-ci
                    // voit deja EN_COURS et refuse au niveau metier.
                    statusRejectionCount.incrementAndGet();
                } else {
                    unexpected.compareAndSet(null, be);
                }
            } catch (Throwable t) {
                unexpected.compareAndSet(null, t);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        exec.submit(worker);
        exec.submit(worker);

        assertTrue(ready.await(10, TimeUnit.SECONDS), "Threads non prets a temps");
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "Threads non termines a temps (deadlock?)");
        exec.shutdownNow();

        assertNull(unexpected.get(), () -> "Exception inattendue : " + unexpected.get());

        // Exactement 1 succes (un seul commencer pose la transition)
        assertEquals(1, successCount.get(),
                "Un seul commencer() doit reussir, observe : success=" + successCount.get()
                        + ", conflict=" + conflictCount.get()
                        + ", statusRejection=" + statusRejectionCount.get());
        // L'autre thread a echoue, soit par optimistic lock soit par check de statut
        assertEquals(1, conflictCount.get() + statusRejectionCount.get(),
                "Le second commencer() doit echouer (optimistic lock OU invalidStatut)");

        // Etat final : tache EN_COURS, version > 0
        TenantContext.set(hotelMrId);
        TacheDto reloaded = tx.execute(s -> tacheService.findById(taskId));
        assertEquals(com.cityprojects.citybackend.entity.menage.StatutTache.EN_COURS, reloaded.statut());
        assertNotNull(reloaded.heureDebutReelle(),
                "heureDebutReelle posee par le commencer() gagnant");
    }
}
