package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link ReservationService}.
 *
 * <p>Couverture :
 * <ol>
 *   <li>T1 : create() genere un numeroReservation {@code RES-{annee}-{codePays}-000001},
 *       persiste l'entite dans le tenant courant, calcule nbNuits et montantTotal,
 *       et genere les nuitees correspondantes.</li>
 *   <li>T2 : findById() depuis un autre tenant -&gt; ResourceNotFoundException
 *       (isolation Hibernate via @TenantId).</li>
 *   <li>T3 : checkIn() puis checkOut() font transitionner statut et chambres
 *       (CONFIRMEE -&gt; ARRIVEE -&gt; PARTIE).</li>
 * </ol>
 *
 * <h3>Strategies importantes</h3>
 * <ul>
 *   <li>Pas de @Transactional sur les methodes de test : on utilise
 *       {@link TransactionTemplate} pour controler precisement quand le tenant
 *       est resolu (cf. pattern de {@code ClientServiceTests}).</li>
 *   <li>SecurityContext force avec un {@link UserPrincipal} factice : le service
 *       lit {@code userId} depuis le SecurityContext (jamais d'un DTO).</li>
 *   <li>Cleanup brut SQL via {@link JdbcTemplate} pour eviter le filtre tenant.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

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
    private Long userMrId;
    private Long clientMrId;
    private Long chambreMrId;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        currentYear = LocalDate.now().getYear();

        // Cleanup ordonne (FK : nuitees -> reservations -> chambres -> types_chambres ;
        // reservations -> clients/dbusers/hotels/societes ; nbusers -> hotels/roles)
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        // Hotels MR + FR
        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        // Role minimum pour creer un user (FK obligatoire sur DBUser.role)
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        // User createur des reservations cote hotel MR
        DBUser user = new DBUser("recept1", "recept1@h1.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Mariam", "Sow", mr, gerant);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(user).getUserId();

        // Catalogue minimal (TypeChambre + Chambre + Client) cote hotel MR
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
                c.setNumeroChambre("101");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            });
            chambreMrId = chambre.getChambreId();

            Client client = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-2026-MR-000099");
                cl.setPrenom("Sidi");
                cl.setNom("Mohamed");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            });
            clientMrId = client.getClientId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM hebergement.nuitees");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_clients");
        jdbcTemplate.update("DELETE FROM hebergement.reservations_chambres");
        jdbcTemplate.update("DELETE FROM hebergement.reservations");
        jdbcTemplate.update("DELETE FROM hebergement.chambres");
        jdbcTemplate.update("DELETE FROM hebergement.types_chambres");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    /**
     * Positionne un {@link UserPrincipal} factice dans le SecurityContext :
     * le service lit {@code userId} depuis le contexte (jamais d'un DTO).
     */
    private void authenticateAs(Long userId, Long hotelId, String roleCode) {
        UserPrincipal principal = new UserPrincipal(
                userId, "test-user", "test@h.test", "pwd",
                "Test", "User", hotelId, "MRH001", "Hotel Test",
                roleCode, roleCode, Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleCode)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - create() genere RES-{annee}-MR-000001, persiste reservation et nuitees, calcule montant")
    void shouldCreateReservationWithGeneratedNumeroAndNuitees() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        LocalDate arrivee = LocalDate.now().plusDays(1);
        LocalDate depart = arrivee.plusDays(3); // 3 nuits
        BigDecimal prix = new BigDecimal("100.00");

        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 2, 0,
                "Vacances", "RAS", BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, prix)),
                null);

        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        assertNotNull(created);
        assertNotNull(created.reservationId(), "id genere par la base");
        assertEquals(String.format("RES-%d-MR-000001", currentYear), created.numeroReservation(),
                "Le numero doit suivre RES-{annee}-{codePays}-{6 chiffres}");
        assertEquals(StatutReservation.CONFIRMEE, created.statut());
        assertEquals(3, created.nbNuits());
        // 3 nuits * 100 = 300, sans reduction
        assertEquals(0, created.montantTotal().compareTo(new BigDecimal("300.00")),
                "Montant brut = 3 nuits * 100 MRU");

        // Les nuitees sont creees (3 lignes)
        Long nbNuitees = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hebergement.nuitees WHERE reservation_id = ?",
                Long.class, created.reservationId());
        assertNotNull(nbNuitees);
        assertEquals(3L, nbNuitees, "3 nuitees doivent etre generees");

        // Le pivot reservations_chambres est cree
        Long nbPivots = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hebergement.reservations_chambres WHERE reservation_id = ?",
                Long.class, created.reservationId());
        assertNotNull(nbPivots);
        assertEquals(1L, nbPivots);
    }

    @Test
    @DisplayName("T2 - findById() depuis un autre tenant -> ResourceNotFoundException (isolation @TenantId)")
    void shouldNotFindCrossTenantReservation() {
        // Cree une reservation dans hotel MR
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        LocalDate arrivee = LocalDate.now().plusDays(2);
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("80.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Tente lecture depuis hotel FR (Hibernate filtre, repository retourne empty)
        TenantContext.set(hotelFrId);
        authenticateAs(99L, hotelFrId, "GERANT");
        Long foreignId = created.reservationId();
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> reservationService.findById(foreignId)));
        assertEquals("error.reservation.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - checkIn() puis checkOut() : transitions CONFIRMEE -> ARRIVEE -> PARTIE et statuts chambres")
    void shouldTransitionThroughCheckInAndCheckOut() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        // Reservation dont la date d'arrivee = aujourd'hui (check-in possible immediat)
        LocalDate arrivee = LocalDate.now();
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("90.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        // Check-in
        ReservationDto afterCheckIn = transactionTemplate.execute(s ->
                reservationService.checkIn(created.reservationId()));
        assertEquals(StatutReservation.ARRIVEE, afterCheckIn.statut());

        Chambre chAfterIn = transactionTemplate.execute(s ->
                chambreRepository.findById(chambreMrId).orElseThrow());
        assertEquals(StatutChambre.OCCUPEE, chAfterIn.getStatut(), "Chambre OCCUPEE apres check-in");

        // Check-out
        ReservationDto afterCheckOut = transactionTemplate.execute(s ->
                reservationService.checkOut(created.reservationId()));
        assertEquals(StatutReservation.PARTIE, afterCheckOut.statut());

        Chambre chAfterOut = transactionTemplate.execute(s ->
                chambreRepository.findById(chambreMrId).orElseThrow());
        assertEquals(StatutChambre.NETTOYAGE, chAfterOut.getStatut(),
                "Chambre passe en NETTOYAGE post check-out (housekeeping)");

        // Toutes les nuitees doivent etre CONSOMMEEs (1 nuit)
        Long nbConsommees = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hebergement.nuitees WHERE reservation_id = ? AND statut = 'CONSOMMEE'",
                Long.class, created.reservationId());
        assertNotNull(nbConsommees);
        assertTrue(nbConsommees >= 1, "Au moins une nuitee CONSOMMEE apres check-out");
    }

    @Test
    @DisplayName("T4 - Tour 12bis C3 : checkIn() sur chambre MAINTENANCE -> BusinessException via ChambreService.changerStatut")
    void shouldRejectCheckInWhenChambreInMaintenance() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        // Pre-condition : place la chambre en MAINTENANCE (DISPONIBLE -> MAINTENANCE est autorise)
        transactionTemplate.execute(s -> {
            Chambre c = chambreRepository.findById(chambreMrId).orElseThrow();
            c.setStatut(StatutChambre.MAINTENANCE);
            return chambreRepository.save(c);
        });

        LocalDate arrivee = LocalDate.now();
        LocalDate depart = arrivee.plusDays(1);
        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, new BigDecimal("90.00"))),
                null);
        ReservationDto created = transactionTemplate.execute(s -> reservationService.create(dto));

        // checkIn() doit propager une BusinessException (transition MAINTENANCE -> OCCUPEE interdite,
        // cf. ChambreServiceImpl.checkTransition).
        Long resId = created.reservationId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> reservationService.checkIn(resId)));
        // Le ChambreService remonte la cle d'erreur de transition.
        assertEquals("error.chambre.transition.toOccupied", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - Tour 12bis C1 : create() rejette le double-booking sur la meme chambre/periode")
    void shouldRejectDoubleBookingOnSameRoom() {
        TenantContext.set(hotelMrId);
        authenticateAs(userMrId, hotelMrId, "GERANT");

        LocalDate arrivee = LocalDate.now().plusDays(2);
        LocalDate depart = arrivee.plusDays(2);
        BigDecimal prix = new BigDecimal("75.00");

        ReservationCreateDto dto = new ReservationCreateDto(
                clientMrId, null, arrivee, depart, 1, 0,
                null, null, BigDecimal.ZERO,
                List.of(new ReservationChambreCreateDto(chambreMrId, null, null, prix)),
                null);

        // 1ere reservation : OK
        transactionTemplate.execute(s -> reservationService.create(dto));

        // 2eme reservation chevauchante (memes dates, meme chambre) : refusee par
        // findConflictsForUpdate (verrou pessimiste cote app, finding C1).
        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> reservationService.create(dto)));
        assertEquals("error.reservation.chambre.conflict", ex.getMessage());
    }
}
