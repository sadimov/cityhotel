package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.NightAuditResultDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link NightAuditService}.
 *
 * <p>Pattern reutilise de {@code NumerotationServiceTests} : un {@link Clock}
 * mutable (TestConfiguration) permet a chaque test de fixer "today" sans toucher
 * au temps systeme. Pas de Spring Security ni de filtre HTTP : on s'arrete a la
 * couche service.</p>
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : reservation CONFIRMEE avec dateArrivee depassee -&gt; NO_SHOW.</li>
 *   <li>T2 : reservation ARRIVEE avec nuitee manquante -&gt; nuitee re-generee.</li>
 *   <li>T3 : reservation deja NO_SHOW -&gt; idempotence (pas de re-marquage).</li>
 *   <li>T4 : reservation CONFIRMEE future -&gt; NON marquee.</li>
 *   <li>T5 : multi-tenant : run() sur hotel A n'affecte pas hotel B.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NightAuditServiceTests.MutableClockConfig.class)
class NightAuditServiceTests {

    /**
     * Configuration de test qui remplace le {@link Clock} par defaut par un
     * proxy mutable. Tous les tests partagent la meme reference et la
     * reinitialisent dans {@link #setUp()}.
     */
    @TestConfiguration
    static class MutableClockConfig {
        static final AtomicReference<Clock> CLOCK_REF =
                new AtomicReference<>(Clock.systemDefaultZone());

        @Bean
        @Primary
        public Clock testClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return CLOCK_REF.get().getZone();
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return CLOCK_REF.get().withZone(zone);
                }

                @Override
                public Instant instant() {
                    return CLOCK_REF.get().instant();
                }
            };
        }
    }

    @Autowired
    private NightAuditService nightAuditService;

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
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationChambreRepository reservationChambreRepository;

    @Autowired
    private NuiteeRepository nuiteeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Long userMrId;
    private Long userFrId;
    private Long clientMrId;
    private Long clientFrId;
    private Long chambreMrId;
    private Long chambreFrId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        // Horloge systeme par defaut - les tests qui figent l'heure peuvent
        // appeler MutableClockConfig.CLOCK_REF.set(...) directement.
        MutableClockConfig.CLOCK_REF.set(Clock.systemDefaultZone());

        cleanAll();

        // Hotels MR + FR
        Hotel mr = new Hotel("MRH001", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FRH001", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        DBUser userMr = new DBUser("recmr", "rec@mr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Mariam", "Sow", mr, gerant);
        userMr.setActif(Boolean.TRUE);
        userMr.setCompteVerrouille(Boolean.FALSE);
        userMrId = userRepository.saveAndFlush(userMr).getUserId();

        DBUser userFr = new DBUser("recfr", "rec@fr.test",
                "$2a$12$placeholderhashplaceholderhashplaceholderhashplacehash",
                "Pierre", "Dupont", fr, gerant);
        userFr.setActif(Boolean.TRUE);
        userFr.setCompteVerrouille(Boolean.FALSE);
        userFrId = userRepository.saveAndFlush(userFr).getUserId();

        // Catalogue minimum cote MR
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
            chambreMrId = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("101");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            }).getChambreId();
            clientMrId = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-MR-001");
                cl.setPrenom("Sidi");
                cl.setNom("Mohamed");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            }).getClientId();
        } finally {
            TenantContext.clear();
        }

        // Catalogue minimum cote FR
        try {
            TenantContext.set(hotelFrId);
            TypeChambre type = transactionTemplate.execute(s -> {
                TypeChambre t = new TypeChambre();
                t.setTypeCode("STD");
                t.setTypeNom("Standard");
                t.setNbLitsMax(2);
                t.setNbPersonnesMax(2);
                t.setActif(Boolean.TRUE);
                return typeChambreRepository.save(t);
            });
            chambreFrId = transactionTemplate.execute(s -> {
                Chambre c = new Chambre();
                c.setNumeroChambre("201");
                c.setTypeId(type.getTypeId());
                c.setStatut(StatutChambre.DISPONIBLE);
                c.setNbLits(1);
                c.setNbPersonnesMax(2);
                c.setActif(Boolean.TRUE);
                return chambreRepository.save(c);
            }).getChambreId();
            clientFrId = transactionTemplate.execute(s -> {
                Client cl = new Client();
                cl.setNumeroClient("CLI-FR-001");
                cl.setPrenom("Pierre");
                cl.setNom("Dupont");
                cl.setActif(Boolean.TRUE);
                return clientRepository.save(cl);
            }).getClientId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MutableClockConfig.CLOCK_REF.set(Clock.systemDefaultZone());
        cleanAll();
    }

    private void cleanAll() {
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
     * Insere directement une reservation CONFIRMEE pour le tenant courant.
     * Le tenant doit etre positionne par l'appelant.
     */
    private Long insertReservation(StatutReservation statut, LocalDate arrivee,
                                    LocalDate depart, Long clientId, Long userId,
                                    String numero) {
        return transactionTemplate.execute(s -> {
            Reservation r = new Reservation();
            r.setNumeroReservation(numero);
            r.setClientPrincipalId(clientId);
            r.setDateArrivee(arrivee);
            r.setDateDepart(depart);
            r.setNbAdultes(1);
            r.setNbEnfants(0);
            r.setStatut(statut);
            r.setReductionPourcentage(BigDecimal.ZERO);
            r.setMontantTotal(new BigDecimal("100.00"));
            r.setUserId(userId);
            return reservationRepository.save(r).getReservationId();
        });
    }

    private void insertPivotChambre(Long reservationId, Long chambreId,
                                    LocalDate debut, LocalDate fin) {
        transactionTemplate.execute(s -> {
            ReservationChambre p = new ReservationChambre();
            p.setReservationId(reservationId);
            p.setChambreId(chambreId);
            p.setDateDebut(debut);
            p.setDateFin(fin);
            p.setPrixNuit(new BigDecimal("100.00"));
            return reservationChambreRepository.save(p);
        });
    }

    private Clock fixedClockOnDate(LocalDate date) {
        // Midi UTC pour eviter les surprises de fuseau.
        Instant instant = date.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant();
        return Clock.fixed(instant, ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("T1 - reservation CONFIRMEE dateArrivee passee -> NO_SHOW")
    void shouldMarkConfirmeeAsNoShowWhenArriveePassed() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        MutableClockConfig.CLOCK_REF.set(fixedClockOnDate(today));

        Long resId;
        try {
            TenantContext.set(hotelMrId);
            // arrivee = today - 1 jour, donc dateArrivee passee
            resId = insertReservation(StatutReservation.CONFIRMEE,
                    today.minusDays(1), today.plusDays(1),
                    clientMrId, userMrId, "RES-2026-MR-T1-001");
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(hotelMrId);
        try {
            NightAuditResultDto result = transactionTemplate.execute(s -> nightAuditService.run());

            assertNotNull(result);
            assertEquals(hotelMrId, result.hotelId());
            assertEquals(today, result.dateExecution());
            assertEquals(1, result.nbReservationsMarkedNoShow(), "1 reservation marquee NO_SHOW");

            Reservation reloaded = transactionTemplate.execute(s ->
                    reservationRepository.findById(resId).orElseThrow());
            assertEquals(StatutReservation.NO_SHOW, reloaded.getStatut());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T2 - reservation ARRIVEE avec nuitee manquante -> regenerer")
    void shouldRegenerateMissingNuitee() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        MutableClockConfig.CLOCK_REF.set(fixedClockOnDate(today));

        Long resId;
        try {
            TenantContext.set(hotelMrId);
            // sejour ARRIVEE : du 12 au 17 (today=15)
            resId = insertReservation(StatutReservation.ARRIVEE,
                    today.minusDays(3), today.plusDays(2),
                    clientMrId, userMrId, "RES-2026-MR-T2-001");
            insertPivotChambre(resId, chambreMrId, today.minusDays(3), today.plusDays(2));

            // Insere 1 seule nuitee (sur le 12) ; il manque celles du 13 et du 14.
            transactionTemplate.execute(s -> {
                Nuitee n = new Nuitee();
                n.setReservationId(resId);
                n.setChambreId(chambreMrId);
                n.setDateNuit(today.minusDays(3));
                n.setPrixNuit(new BigDecimal("100.00"));
                n.setTaxeSejour(BigDecimal.ZERO);
                n.setStatut(StatutNuitee.CONSOMMEE);
                return nuiteeRepository.save(n);
            });
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(hotelMrId);
        try {
            NightAuditResultDto result = transactionTemplate.execute(s -> nightAuditService.run());

            assertNotNull(result);
            // periode [today-3, today) = 3 jours, 1 deja presente -> 2 a generer
            assertEquals(2, result.nbNuiteesManquantesGenerees(),
                    "2 nuitees manquantes regenerees (today-2 et today-1)");

            // Verifie qu'il y a bien 3 nuitees au total maintenant
            List<Nuitee> nuitees = transactionTemplate.execute(s ->
                    nuiteeRepository.findByReservationIdOrderByDateNuitAsc(resId));
            assertEquals(3, nuitees.size(), "3 nuitees au total apres regeneration");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T3 - reservation deja NO_SHOW -> idempotence (pas de re-marquage)")
    void shouldBeIdempotentOnAlreadyNoShow() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        MutableClockConfig.CLOCK_REF.set(fixedClockOnDate(today));

        try {
            TenantContext.set(hotelMrId);
            insertReservation(StatutReservation.NO_SHOW,
                    today.minusDays(2), today.plusDays(1),
                    clientMrId, userMrId, "RES-2026-MR-T3-001");
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(hotelMrId);
        try {
            NightAuditResultDto result = transactionTemplate.execute(s -> nightAuditService.run());
            assertEquals(0, result.nbReservationsMarkedNoShow(),
                    "Aucune reservation re-marquee : la NO_SHOW existante est ignoree");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T4 - reservation CONFIRMEE future -> NON marquee NO_SHOW")
    void shouldNotMarkFutureConfirmeeAsNoShow() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        MutableClockConfig.CLOCK_REF.set(fixedClockOnDate(today));

        Long resId;
        try {
            TenantContext.set(hotelMrId);
            // dateArrivee dans le futur
            resId = insertReservation(StatutReservation.CONFIRMEE,
                    today.plusDays(1), today.plusDays(3),
                    clientMrId, userMrId, "RES-2026-MR-T4-001");
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(hotelMrId);
        try {
            NightAuditResultDto result = transactionTemplate.execute(s -> nightAuditService.run());
            assertEquals(0, result.nbReservationsMarkedNoShow(),
                    "Reservation future = pas marquee NO_SHOW");

            Reservation reloaded = transactionTemplate.execute(s ->
                    reservationRepository.findById(resId).orElseThrow());
            assertEquals(StatutReservation.CONFIRMEE, reloaded.getStatut(),
                    "Statut inchange : reste CONFIRMEE");
            assertNotEquals(StatutReservation.NO_SHOW, reloaded.getStatut());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T5 - multi-tenant : run() sur hotel A n'affecte pas hotel B")
    void shouldBeIsolatedAcrossTenants() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        MutableClockConfig.CLOCK_REF.set(fixedClockOnDate(today));

        // Cote MR : reservation CONFIRMEE depassee
        Long resMrId;
        try {
            TenantContext.set(hotelMrId);
            resMrId = insertReservation(StatutReservation.CONFIRMEE,
                    today.minusDays(1), today.plusDays(1),
                    clientMrId, userMrId, "RES-2026-MR-T5-001");
        } finally {
            TenantContext.clear();
        }

        // Cote FR : reservation CONFIRMEE depassee egalement
        Long resFrId;
        try {
            TenantContext.set(hotelFrId);
            resFrId = insertReservation(StatutReservation.CONFIRMEE,
                    today.minusDays(2), today.plusDays(1),
                    clientFrId, userFrId, "RES-2026-FR-T5-001");
        } finally {
            TenantContext.clear();
        }

        // Run cote MR uniquement
        TenantContext.set(hotelMrId);
        NightAuditResultDto resultMr;
        try {
            resultMr = transactionTemplate.execute(s -> nightAuditService.run());
        } finally {
            TenantContext.clear();
        }
        assertEquals(hotelMrId, resultMr.hotelId());
        assertEquals(1, resultMr.nbReservationsMarkedNoShow());

        // Verifie cote MR : reservation NO_SHOW
        TenantContext.set(hotelMrId);
        try {
            Reservation mr = transactionTemplate.execute(s ->
                    reservationRepository.findById(resMrId).orElseThrow());
            assertEquals(StatutReservation.NO_SHOW, mr.getStatut());
        } finally {
            TenantContext.clear();
        }

        // Verifie cote FR : reservation TOUJOURS CONFIRMEE (le run MR ne l'a pas touche)
        TenantContext.set(hotelFrId);
        try {
            Reservation fr = transactionTemplate.execute(s ->
                    reservationRepository.findById(resFrId).orElseThrow());
            assertEquals(StatutReservation.CONFIRMEE, fr.getStatut(),
                    "FR non touche : isolation tenant respectee");
        } finally {
            TenantContext.clear();
        }

        // Maintenant run cote FR : doit traiter sa propre reservation
        TenantContext.set(hotelFrId);
        NightAuditResultDto resultFr;
        try {
            resultFr = transactionTemplate.execute(s -> nightAuditService.run());
        } finally {
            TenantContext.clear();
        }
        assertEquals(hotelFrId, resultFr.hotelId());
        assertEquals(1, resultFr.nbReservationsMarkedNoShow(),
                "Run cote FR marque sa propre reservation");

        TenantContext.set(hotelFrId);
        try {
            Reservation fr = transactionTemplate.execute(s ->
                    reservationRepository.findById(resFrId).orElseThrow());
            assertEquals(StatutReservation.NO_SHOW, fr.getStatut());
            assertTrue(true, "OK isolation respectee dans les deux sens");
        } finally {
            TenantContext.clear();
        }

        // Eteint warnings
        Collections.emptyList();
    }
}
