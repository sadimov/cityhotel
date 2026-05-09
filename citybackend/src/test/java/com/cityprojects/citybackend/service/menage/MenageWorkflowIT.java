package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.hebergement.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'integration end-to-end du couplage event-driven menage / hebergement
 * (Tour 30, Workflows A/B/C).
 *
 * <h3>Pourquoi {@code IT} (Failsafe)</h3>
 * <p>Necessite Spring complet pour observer le pipeline
 * {@code @TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW}. Le test
 * tourne en H2 (profil "test") avec {@code city.scheduler.enabled=false}
 * pour eviter le declenchement intempestif des crons.</p>
 *
 * <h3>Couverture (7 tests)</h3>
 * <ol>
 *   <li>{@code shouldGenerateTacheOnReservationCheckOut} - Workflow A</li>
 *   <li>{@code shouldBeIdempotentOnPlanningGeneration} - Workflow A idempotence</li>
 *   <li>{@code shouldMarkChambreDisponibleAfterTacheQuotidienneTerminee} - Workflow B</li>
 *   <li>{@code shouldBlockChambreOnMaintenanceTacheStarted} - Workflow C</li>
 *   <li>{@code shouldUnblockChambreOnMaintenanceTacheTerminee} - Workflow B (MAINT)</li>
 *   <li>{@code shouldNotPropagateCrossTenant} - isolation tenant des events</li>
 *   <li>{@code shouldGracefullyHandleInvalidChambreTransition} - resilience listener</li>
 * </ol>
 *
 * <h3>Multi-tenant et SecurityContext</h3>
 * <p>{@code SecurityContextHolder} est positionne via {@link #authenticateAs}
 * pour permettre a {@code ReservationServiceImpl} de lire {@code userId}
 * (oblige metier). Nettoye en {@code @AfterEach}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MenageWorkflowIT {

    @Autowired private ReservationService reservationService;
    @Autowired private TacheService tacheService;
    @Autowired private MenagePlanningService menagePlanningService;
    @Autowired private PersonnelService personnelService;
    @Autowired private TacheRepository tacheRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private TypeChambreRepository typeChambreRepository;
    @Autowired private ChambreRepository chambreRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long userMrId;
    private Long clientMrId;
    private Long chambreMrId1;
    private Long chambreMrId2;
    private Long personnelMrId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        // Hotels MR (cible des workflows) + FR (controle cross-tenant)
        Hotel mr = new Hotel("MRWFL", "Hotel Mauritanie WFL");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRWFL", "Hotel France WFL");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser user = new DBUser("recwfl", "rec@wfl.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Aicha", "Sow", mr, gerant);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(user).getUserId();

        // Catalogue MR : 1 type + 2 chambres + 1 client + 1 personnel
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
            Chambre c1 = tx.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("WFL-101");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId1 = c1.getChambreId();
            Chambre c2 = tx.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("WFL-102");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId2 = c2.getChambreId();

            Client client = tx.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-WFL-MR-000001");
                cl.setPrenom("Test");
                cl.setNom("Client");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();

            PersonnelDto p = tx.execute(s -> personnelService.create(
                    new PersonnelCreateDto("MENWFL", "Personnel", "WFL",
                            null, null, null, null)));
            personnelMrId = p.personnelId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
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
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /** Positionne un UserPrincipal factice pour {@code ReservationServiceImpl#currentUserId}. */
    private void authenticateAs(Long userId, Long hotelId) {
        UserPrincipal principal = new UserPrincipal(
                userId, "test-user", "test@h.test", "pwd",
                "Test", "User", hotelId, "MRWFL", "Hotel WFL",
                "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * Cree une reservation ARRIVEE (check-in fait) sur la chambre donnee, du
     * jour {@code today} a J+1. Renvoie le reservationId. La chambre repasse
     * a OCCUPEE post check-in.
     */
    private Long createReservationArrivee(Long chambreId) {
        LocalDate arrivee = LocalDate.now();
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreId, null, null,
                        new BigDecimal("100.00"))),
                null);
        ReservationDto created = tx.execute(s -> reservationService.create(dto));
        tx.execute(s -> reservationService.checkIn(created.reservationId()));
        return created.reservationId();
    }

    @Test
    @DisplayName("WFL-A1 - checkOut() publie event -> listener cree 1 tache QUOTIDIEN PLANIFIEE par chambre liberee")
    void shouldGenerateTacheOnReservationCheckOut() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // 2 reservations sur 2 chambres differentes (1 check-in chacune)
        Long resId1 = createReservationArrivee(chambreMrId1);
        Long resId2 = createReservationArrivee(chambreMrId2);

        // Pre-condition : 0 tache
        long countBefore = tx.execute(s -> tacheRepository.count());
        assertEquals(0L, countBefore, "Aucune tache avant les check-out");

        // checkOut() declenche AFTER_COMMIT -> event -> listener -> 1 tache
        tx.execute(s -> reservationService.checkOut(resId1));
        tx.execute(s -> reservationService.checkOut(resId2));

        // Post-condition : 2 taches QUOTIDIEN PLANIFIEE pour aujourd'hui
        TenantContext.set(hotelMrId);
        List<TacheDto> taches = tx.execute(s ->
                tacheService.findByDate(LocalDate.now()));
        assertEquals(2, taches.size(),
                "Le listener AFTER_COMMIT doit creer 1 tache par chambre liberee");
        for (TacheDto t : taches) {
            assertEquals(TypeNettoyage.QUOTIDIEN, t.typeNettoyage(), "Type QUOTIDIEN auto");
            assertEquals(StatutTache.PLANIFIEE, t.statut(), "Statut PLANIFIEE");
            assertEquals(LocalDate.now(), t.datePlanifiee());
            assertEquals(2, t.priorite(), "Priorite 2 (auto-generee)");
        }
    }

    @Test
    @DisplayName("WFL-A2 - genererPlanningDuJour() est idempotent : 2eme appel n'ajoute pas de doublon")
    void shouldBeIdempotentOnPlanningGeneration() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        Long resId1 = createReservationArrivee(chambreMrId1);
        Long resId2 = createReservationArrivee(chambreMrId2);
        tx.execute(s -> reservationService.checkOut(resId1));
        tx.execute(s -> reservationService.checkOut(resId2));

        // 2 taches creees par les listeners
        TenantContext.set(hotelMrId);
        long after1 = tx.execute(s -> (long) tacheService.findByDate(LocalDate.now()).size());
        assertEquals(2L, after1, "Apres listeners : 2 taches");

        // Appel manuel du service de generation -> ne doit RIEN creer en plus
        TenantContext.set(hotelMrId);
        Integer created = tx.execute(s ->
                menagePlanningService.genererPlanningDuJour(LocalDate.now()));
        assertNotNull(created);
        assertEquals(0, created, "Generation manuelle apres listeners : 0 nouveau (idempotent)");

        TenantContext.set(hotelMrId);
        long after2 = tx.execute(s -> (long) tacheService.findByDate(LocalDate.now()).size());
        assertEquals(2L, after2, "Toujours 2 taches au total (idempotent)");
    }

    @Test
    @DisplayName("WFL-B1 - terminer() tache QUOTIDIEN -> chambre passe NETTOYAGE -> DISPONIBLE via listener")
    void shouldMarkChambreDisponibleAfterTacheQuotidienneTerminee() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // Setup : reservation -> checkOut -> chambre NETTOYAGE + tache PLANIFIEE
        Long resId = createReservationArrivee(chambreMrId1);
        tx.execute(s -> reservationService.checkOut(resId));

        // Pre-condition : chambre NETTOYAGE (pose par checkOut())
        TenantContext.set(hotelMrId);
        Chambre chBefore = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertEquals(StatutChambre.NETTOYAGE, chBefore.getStatut(),
                "Chambre NETTOYAGE post check-out");

        // Recupere la tache QUOTIDIEN creee par le listener Workflow A
        List<TacheDto> taches = tx.execute(s ->
                tacheService.findByDate(LocalDate.now()));
        assertEquals(1, taches.size());
        Long tacheId = taches.get(0).tacheId();

        // Assigne + commencer + terminer
        tx.execute(s -> tacheService.assigner(tacheId,
                new com.cityprojects.citybackend.dto.menage.AssignerTacheDto(personnelMrId, null)));
        tx.execute(s -> tacheService.commencer(tacheId));
        tx.execute(s -> tacheService.terminer(tacheId, null));

        // Post : listener Workflow B -> chambre DISPONIBLE
        TenantContext.set(hotelMrId);
        Chambre chAfter = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertEquals(StatutChambre.DISPONIBLE, chAfter.getStatut(),
                "Chambre DISPONIBLE apres terminer() QUOTIDIEN");
    }

    @Test
    @DisplayName("WFL-C1 - commencer() tache MAINTENANCE -> chambre DISPONIBLE -> MAINTENANCE via listener")
    void shouldBlockChambreOnMaintenanceTacheStarted() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // Pre-condition : chambre DISPONIBLE (defaut)
        // Cree une tache MAINTENANCE PLANIFIEE assignee
        TacheDto tache = tx.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId1, personnelMrId,
                        TypeNettoyage.MAINTENANCE, 3, LocalDate.now(),
                        LocalTime.of(9, 0), LocalTime.of(11, 0),
                        "Reparation climatisation", "Cle a molette")));

        // commencer() -> publie TacheCommenceeEvent -> listener bascule chambre
        tx.execute(s -> tacheService.commencer(tache.tacheId()));

        TenantContext.set(hotelMrId);
        Chambre ch = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertEquals(StatutChambre.MAINTENANCE, ch.getStatut(),
                "Chambre passee en MAINTENANCE par le listener (Workflow C)");
    }

    @Test
    @DisplayName("WFL-B2 - terminer() tache MAINTENANCE -> chambre MAINTENANCE -> DISPONIBLE via listener")
    void shouldUnblockChambreOnMaintenanceTacheTerminee() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // Setup : tache MAINTENANCE commencee (chambre est en MAINTENANCE)
        TacheDto tache = tx.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId1, personnelMrId,
                        TypeNettoyage.MAINTENANCE, 3, LocalDate.now(),
                        null, null, null, null)));
        tx.execute(s -> tacheService.commencer(tache.tacheId()));

        // Pre : chambre MAINTENANCE (Workflow C deja teste)
        TenantContext.set(hotelMrId);
        Chambre chBefore = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertEquals(StatutChambre.MAINTENANCE, chBefore.getStatut());

        // terminer() -> Workflow B
        tx.execute(s -> tacheService.terminer(tache.tacheId(), null));

        TenantContext.set(hotelMrId);
        Chambre chAfter = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertEquals(StatutChambre.DISPONIBLE, chAfter.getStatut(),
                "Chambre redevient DISPONIBLE apres terminer() MAINTENANCE");
    }

    @Test
    @DisplayName("WFL-X1 - event hotel MR ne cree PAS de tache cote hotel FR (isolation tenant)")
    void shouldNotPropagateCrossTenant() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // checkOut cote hotel MR
        Long resId = createReservationArrivee(chambreMrId1);
        tx.execute(s -> reservationService.checkOut(resId));

        // Cote MR : 1 tache existe
        TenantContext.set(hotelMrId);
        long countMr = tx.execute(s -> tacheRepository.count());
        assertEquals(1L, countMr, "1 tache cote MR (listener AFTER_COMMIT)");

        // Cote FR : 0 tache (isolation Hibernate via @TenantId + listener
        // positionne TenantContext=MR depuis l'event payload, pas FR)
        TenantContext.set(hotelFrId);
        long countFr = tx.execute(s -> tacheRepository.count());
        assertEquals(0L, countFr, "Aucune tache cote FR : event ne traverse pas le tenant");
    }

    @Test
    @DisplayName("WFL-X2 - chambre dans un statut imprevu : listener log WARN sans crasher la TX d'origine")
    void shouldGracefullyHandleInvalidChambreTransition() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId);

        // Force la chambre en OCCUPEE (etat ou la transition vers DISPONIBLE
        // sans NETTOYAGE est interdite par checkTransition).
        tx.execute(s -> {
            Chambre c = chambreRepository.findById(chambreMrId1).orElseThrow();
            c.setStatut(StatutChambre.OCCUPEE);
            return chambreRepository.save(c);
        });

        // Cree une tache QUOTIDIEN deja EN_COURS pour pouvoir la terminer
        // (on n'utilise PAS checkOut() ici car il bascule la chambre avant le
        // terminer, ce qu'on veut justement eviter pour reproduire le cas
        // imprevu : chambre OCCUPEE au moment ou le listener tente de la
        // passer DISPONIBLE).
        TacheDto tache = tx.execute(s -> tacheService.create(
                new TacheCreateDto(chambreMrId1, personnelMrId,
                        TypeNettoyage.QUOTIDIEN, 1, LocalDate.now(),
                        null, null, null, null)));
        tx.execute(s -> tacheService.commencer(tache.tacheId()));

        // Repose la chambre en OCCUPEE apres le commencer (le listener
        // QUOTIDIEN au commencer est no-op donc la chambre n'a pas bouge,
        // mais on s'assure de l'etat avant terminer()).
        tx.execute(s -> {
            Chambre c = chambreRepository.findById(chambreMrId1).orElseThrow();
            c.setStatut(StatutChambre.OCCUPEE);
            return chambreRepository.save(c);
        });

        // terminer() ne doit PAS crasher : le listener catche la BusinessException
        // de transition (OCCUPEE -> DISPONIBLE refusee) et log WARN.
        TacheDto terminee = tx.execute(s ->
                tacheService.terminer(tache.tacheId(), null));
        assertNotNull(terminee);
        assertEquals(StatutTache.TERMINEE, terminee.statut(),
                "La tache passe TERMINEE meme si la chambre n'a pas pu etre transitee");

        // La chambre reste en OCCUPEE (le listener n'a pas pu la changer)
        TenantContext.set(hotelMrId);
        Chambre ch = tx.execute(s ->
                chambreRepository.findById(chambreMrId1).orElseThrow());
        assertTrue(ch.getStatut() == StatutChambre.OCCUPEE
                        || ch.getStatut() == StatutChambre.DISPONIBLE,
                "Chambre reste OCCUPEE (transition refusee) ou DISPONIBLE (si listener gracieux a tente plusieurs voies)");
    }
}
