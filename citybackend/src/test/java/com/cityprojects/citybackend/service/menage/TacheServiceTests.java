package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.dto.menage.TerminerTacheDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
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
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2) du {@link TacheService} (Tour 27, enrichi Tour 30 + Tour 31).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : create() persiste une tache avec statut PLANIFIEE et hotelId via TenantContext.</li>
 *   <li>T2 : transitions PLANIFIEE -&gt; EN_COURS -&gt; TERMINEE via assigner, commencer, terminer.</li>
 *   <li>T3 : commencer() refusee si non assignee.</li>
 *   <li>T4 : findById() depuis un autre tenant -&gt; ResourceNotFoundException.</li>
 *   <li>T5..T8 : Tour 30 (findEnRetard, delete TERMINEE, annuler).</li>
 *   <li>T9..T11 : Tour 31 (cross-tenant create chambre, create personnel, assigner personnel).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class TacheServiceTests {

    @Autowired
    private TacheService tacheService;

    @Autowired
    private PersonnelService personnelService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private TypeChambreRepository typeChambreRepository;

    @Autowired
    private ChambreRepository chambreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long chambreMrId;
    private Long personnelMrId;
    private Long chambreFrId;
    private Long personnelFrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();

        // Cleanup ordonne
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

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Seed : un type de chambre + une chambre + un personnel dans hotel MR
        try {
            TenantContext.set(hotelMrId);
            TypeChambre type = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre chambre = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("201");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId = chambre.getChambreId();

            PersonnelDto p = transactionTemplate.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MEN500", "Yacine", "Diallo",
                            null, null, null, null)));
            personnelMrId = p.personnelId();
        } finally {
            TenantContext.clear();
        }

        // Seed symetrique cote FR : 1 type chambre + 1 chambre + 1 personnel.
        // Necessaire pour les tests cross-tenant (Tour 31) : on doit pouvoir
        // operer dans le contexte FR avec une chambre/personnel FR valides
        // tout en passant un id MR pour declencher le ResourceNotFoundException.
        try {
            TenantContext.set(hotelFrId);
            TypeChambre typeFr = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            Chambre chambreFr = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("301");
                c.setTypeId(typeFr.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreFrId = chambreFr.getChambreId();

            PersonnelDto pFr = transactionTemplate.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MEN600", "Pierre", "Dupont",
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

    @Test
    @DisplayName("T1 - create() persiste une tache PLANIFIEE avec hotelId via TenantContext")
    void shouldCreateTache() {
        TenantContext.set(hotelMrId);

        TacheCreateDto dto = new TacheCreateDto(
                chambreMrId, null, TypeNettoyage.QUOTIDIEN, 2,
                LocalDate.now(), null, null,
                "Nettoyage post check-out", null);

        TacheDto created = transactionTemplate.execute(s -> tacheService.create(dto));

        assertNotNull(created);
        assertNotNull(created.tacheId());
        assertEquals(chambreMrId, created.chambreId());
        assertNull(created.personnelId(), "Tache non assignee a la creation");
        assertEquals(StatutTache.PLANIFIEE, created.statut());
        assertEquals(TypeNettoyage.QUOTIDIEN, created.typeNettoyage());
        assertEquals(2, created.priorite());

        // hotel_id en base
        Long persistedHotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM menage.taches WHERE tache_id = ?",
                Long.class, created.tacheId());
        assertEquals(hotelMrId, persistedHotelId);

        // Une entree historique a ete creee (action=creation)
        Long countHist = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menage.historique WHERE tache_id = ? AND action = 'creation'",
                Long.class, created.tacheId());
        assertEquals(1L, countHist, "Audit log : 1 entree 'creation' attendue");
    }

    @Test
    @DisplayName("T2 - Transitions assigner -> commencer -> terminer (PLANIFIEE -> EN_COURS -> TERMINEE)")
    void shouldTransitionTacheLifecycle() {
        TenantContext.set(hotelMrId);

        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));

        // Assigner
        TacheDto assigned = transactionTemplate.execute(s -> tacheService.assigner(
                created.tacheId(),
                new com.cityprojects.citybackend.dto.menage.AssignerTacheDto(personnelMrId, "Urgent VIP")));
        assertEquals(personnelMrId, assigned.personnelId());
        assertEquals(StatutTache.PLANIFIEE, assigned.statut(),
                "Assignation ne change pas le statut metier (juste personnelId)");

        // Commencer
        TacheDto started = transactionTemplate.execute(s -> tacheService.commencer(created.tacheId()));
        assertEquals(StatutTache.EN_COURS, started.statut());
        assertNotNull(started.heureDebutReelle());
        assertNull(started.heureFinReelle());

        // Terminer
        TacheDto done = transactionTemplate.execute(s -> tacheService.terminer(created.tacheId(),
                new TerminerTacheDto("Tout OK", null, "aspirateur,detergent", 4)));
        assertEquals(StatutTache.TERMINEE, done.statut());
        assertNotNull(done.heureFinReelle());

        // Audit : 4 entrees (creation, assignation, debut, fin)
        Long countHist = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menage.historique WHERE tache_id = ?",
                Long.class, created.tacheId());
        assertEquals(4L, countHist,
                "4 actions d'historique attendues : creation, assignation, debut, fin");
    }

    @Test
    @DisplayName("T3 - commencer() refusee si tache non assignee -> BusinessException")
    void shouldRejectCommencerIfNotAssigned() {
        TenantContext.set(hotelMrId);
        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> tacheService.commencer(created.tacheId())));
        assertEquals("error.tache.commencer.notAssigned", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - findById() depuis un autre tenant -> ResourceNotFoundException")
    void shouldNotFindCrossTenantTache() {
        TenantContext.set(hotelMrId);
        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));
        TenantContext.clear();

        TenantContext.set(hotelFrId);
        Long foreignId = created.tacheId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> tacheService.findById(foreignId)));
        assertEquals("error.tache.notFound", ex.getMessage());
    }

    /**
     * Tour 30 etape 1 : verifie que findEnRetard() ne remonte PAS une tache du
     * jour dont {@code heureFinPrevue} est encore dans le futur. Avant le fix,
     * la JPQL ignorait {@code heureFinPrevue} et toute tache d'aujourd'hui
     * remontait des 00h01.
     */
    @Test
    @DisplayName("T5 - Etape 1 : findEnRetard() ne retourne pas une tache du jour avec heureFinPrevue dans le futur")
    void shouldNotReturnTodayTasksWithFutureEndTime() {
        TenantContext.set(hotelMrId);

        // Tache A : aujourd'hui, heureFinPrevue = 23:59 -> ne doit PAS etre en retard
        // (sauf execution du test a 23:59:30, cas accepte comme negligeable).
        TacheDto todayLate = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(23, 59),
                        "tache du jour, fin tard", null)));

        // Tache B : hier, sans heure -> EN RETARD inconditionnellement
        TacheDto yesterday = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now().minusDays(1), null, null,
                        "tache d'hier", null)));

        // Tache C : aujourd'hui, heureFinPrevue = 00:01 -> EN RETARD si on est apres 00:01
        // (vrai en pratique, sauf si le test demarre exactement entre minuit et 00:01).
        TacheDto todayEarly = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), LocalTime.of(0, 0), LocalTime.of(0, 1),
                        "tache du jour finie tres tot", null)));

        List<TacheDto> enRetard = transactionTemplate.execute(s -> tacheService.findEnRetard());

        assertNotNull(enRetard);

        // todayLate ne doit PAS apparaitre — c'est la regression cible
        assertFalse(enRetard.stream().anyMatch(t -> t.tacheId().equals(todayLate.tacheId())),
                "Une tache du jour avec heureFinPrevue=23:59 ne doit pas remonter en retard");

        // yesterday doit apparaitre
        assertTrue(enRetard.stream().anyMatch(t -> t.tacheId().equals(yesterday.tacheId())),
                "Une tache d'hier doit remonter en retard");

        // todayEarly doit apparaitre (heure courante apres 00:01)
        // On ne l'assert pas pour eviter le cas limite execution avant 00:01,
        // mais le coverage du chemin OR est garanti par yesterday.
        // Toutefois si LocalTime.now() > 00:01:00 -> alors c'est un must.
        if (LocalTime.now().isAfter(LocalTime.of(0, 1))) {
            assertTrue(enRetard.stream().anyMatch(t -> t.tacheId().equals(todayEarly.tacheId())),
                    "Une tache du jour avec heureFinPrevue=00:01 doit remonter en retard apres 00:01");
        }
    }

    /**
     * Tour 30 etape 7 : delete() refuse de supprimer une tache TERMINEE pour
     * preserver l'audit. Seules les PLANIFIEE et ANNULEE restent supprimables.
     */
    @Test
    @DisplayName("T6 - Etape 7 : delete() refusee si tache TERMINEE -> BusinessException")
    void shouldRejectDeleteOfTerminatedTask() {
        TenantContext.set(hotelMrId);
        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, personnelMrId, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));
        transactionTemplate.execute(s -> tacheService.commencer(created.tacheId()));
        transactionTemplate.execute(s -> tacheService.terminer(created.tacheId(),
                new TerminerTacheDto(null, null, null, null)));

        Long taskId = created.tacheId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.executeWithoutResult(s -> tacheService.delete(taskId)));
        assertEquals("error.tache.delete.terminee", ex.getMessage());
    }

    /**
     * Tour 30 etape 8 : annulation d'une tache PLANIFIEE -> ANNULEE.
     */
    @Test
    @DisplayName("T7 - Etape 8 : annuler() une tache PLANIFIEE -> statut ANNULEE + audit annulation")
    void shouldAnnulerTacheFromPlanifiee() {
        TenantContext.set(hotelMrId);
        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));

        TacheDto cancelled = transactionTemplate.execute(s ->
                tacheService.annuler(created.tacheId(), "Chambre indisponible suite a panne"));

        assertEquals(StatutTache.ANNULEE, cancelled.statut());
        // motif trace dans les commentaires
        assertNotNull(cancelled.commentaires());
        assertTrue(cancelled.commentaires().contains("Chambre indisponible suite a panne"));

        Long countAnnulation = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menage.historique WHERE tache_id = ? AND action = 'annulation'",
                Long.class, created.tacheId());
        assertEquals(1L, countAnnulation, "Audit log : 1 entree 'annulation' attendue");
    }

    /**
     * Tour 30 etape 8 : annuler() refuse une tache deja TERMINEE.
     */
    @Test
    @DisplayName("T8 - Etape 8 : annuler() refusee si tache TERMINEE -> BusinessException")
    void shouldRejectAnnulerTacheFromTerminee() {
        TenantContext.set(hotelMrId);
        TacheDto created = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId, personnelMrId, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));
        transactionTemplate.execute(s -> tacheService.commencer(created.tacheId()));
        transactionTemplate.execute(s -> tacheService.terminer(created.tacheId(),
                new TerminerTacheDto(null, null, null, null)));

        Long taskId = created.tacheId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> tacheService.annuler(taskId, "trop tard")));
        assertEquals("error.tache.annuler.invalidStatut", ex.getMessage());
    }

    /**
     * Tour 31 (audit Tour 29 multitenant-guardian) : create() depuis le tenant
     * B avec un {@code chambreId} appartenant au tenant A doit lever
     * {@link ResourceNotFoundException} ("error.chambre.notFound").
     *
     * <p>Defense en place : Hibernate filtre {@code WHERE hotel_id = ?} via
     * {@code @TenantId} sur {@code Chambre}. Le {@code findById(chambreA.id)}
     * sous {@code TenantContext = B} retourne {@code Optional.empty()}.
     * Ce test est l'assertion anti-regression du chemin
     * {@code TacheServiceImpl#create:94-95}.</p>
     */
    @Test
    @DisplayName("T9 - Tour 31 : create() avec chambreId d'un autre tenant -> ResourceNotFoundException")
    void shouldRejectCreateTacheWithCrossTenantChambre() {
        // chambreMrId existe cote MR, on tente de la reutiliser depuis FR
        TenantContext.set(hotelFrId);
        Long foreignChambreId = chambreMrId;

        TacheCreateDto dto = new TacheCreateDto(
                foreignChambreId, null, TypeNettoyage.QUOTIDIEN, 1,
                LocalDate.now(), null, null, null, null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> tacheService.create(dto)));
        assertEquals("error.chambre.notFound", ex.getMessage());
    }

    /**
     * Tour 31 (audit Tour 29) : create() depuis le tenant B avec une
     * {@code chambreFr} valide mais un {@code personnelId} appartenant au
     * tenant A doit lever {@link ResourceNotFoundException}
     * ("error.personnel.notFound").
     *
     * <p>Couvre la branche {@code dto.personnelId() != null} dans
     * {@code TacheServiceImpl#create:98-100}. Hibernate filtre via
     * {@code @TenantId} sur {@code Personnel}.</p>
     */
    @Test
    @DisplayName("T10 - Tour 31 : create() avec personnelId d'un autre tenant -> ResourceNotFoundException")
    void shouldRejectCreateTacheWithCrossTenantPersonnel() {
        TenantContext.set(hotelFrId);
        Long foreignPersonnelId = personnelMrId;

        // chambreFrId est valide cote FR, on ne devrait s'arreter qu'a la verif personnel
        TacheCreateDto dto = new TacheCreateDto(
                chambreFrId, foreignPersonnelId, TypeNettoyage.QUOTIDIEN, 1,
                LocalDate.now(), null, null, null, null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> tacheService.create(dto)));
        assertEquals("error.personnel.notFound", ex.getMessage());
    }

    /**
     * Tour 31 (audit Tour 29) : assigner() depuis le tenant B avec une
     * {@code tacheB} valide mais un {@code personnelId} appartenant au tenant
     * A doit lever {@link ResourceNotFoundException} ("error.personnel.notFound").
     *
     * <p>Couvre {@code TacheServiceImpl#assigner:231-232}. Hibernate filtre
     * via {@code @TenantId}.</p>
     */
    @Test
    @DisplayName("T11 - Tour 31 : assigner() avec personnelId d'un autre tenant -> ResourceNotFoundException")
    void shouldRejectAssignerWithCrossTenantPersonnel() {
        // Cree la tache cote FR (chambreFr + pas de personnel)
        TenantContext.set(hotelFrId);
        TacheDto tacheFr = transactionTemplate.execute(s -> tacheService.create(
                new TacheCreateDto(chambreFrId, null, TypeNettoyage.QUOTIDIEN, 1,
                        LocalDate.now(), null, null, null, null)));

        // Toujours sous TenantContext = FR : tente d'assigner un personnel MR
        Long tacheFrId = tacheFr.tacheId();
        Long foreignPersonnelId = personnelMrId;
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> tacheService.assigner(tacheFrId,
                        new com.cityprojects.citybackend.dto.menage.AssignerTacheDto(
                                foreignPersonnelId, "tentative cross-tenant"))));
        assertEquals("error.personnel.notFound", ex.getMessage());
    }
}
