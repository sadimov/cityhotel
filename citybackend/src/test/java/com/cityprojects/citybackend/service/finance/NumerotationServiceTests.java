package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.repository.core.HotelRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (rapides, en H2) du {@link NumerotationService}.
 * <p>
 * Couvre :
 * <ol>
 *   <li>T1 : premier appel pour un hotel donne -&gt; suffix 000001 + bon prefixe.</li>
 *   <li>T2 : 3 appels consecutifs -&gt; 000001, 000002, 000003.</li>
 *   <li>T3 : sequences independantes par type (FACT vs PAY).</li>
 *   <li>T4 : sequences independantes par hotel (TenantContext switch).</li>
 *   <li>T5 : sans TenantContext -&gt; @RequireTenant rejette (IllegalStateException).</li>
 *   <li>T6 : changement d'exercice (mock Clock) -&gt; reset a 000001.</li>
 *   <li>T7 : format strict {TYPE}-{exercice}-{codePays}-{6 chiffres}.</li>
 * </ol>
 *
 * <h3>Strategies importantes</h3>
 * <ul>
 *   <li><b>Clock mockable</b> : on injecte un {@link AtomicReference}{@code <Clock>}
 *       via une {@link TestConfiguration} pour pouvoir piloter l'annee
 *       (utilise dans T6). Les autres tests utilisent l'horloge systeme.</li>
 *   <li><b>Pas de @Transactional sur les tests</b> : on utilise
 *       {@link TransactionTemplate} pour controler l'ouverture des transactions
 *       et garantir que le tenant est resolu au bon moment, exactement comme
 *       dans {@code TenantMultiTenancyIT}.</li>
 *   <li><b>Cleanup brut SQL</b> : DELETE direct via {@link JdbcTemplate} pour
 *       eviter les interactions avec le filtre tenant Hibernate.</li>
 * </ul>
 *
 * <p><b>Note</b> : meme si ces tests demarrent Spring + JPA, ils restent dans
 * la categorie "rapide" et sont nommes {@code *Tests} pour etre executes par
 * Surefire au goal {@code test}, conformement a la convention CLAUDE.md §7.
 * Les vrais tests de concurrence (10 threads en parallele sur Postgres reel)
 * vivent dans {@code NumerotationServiceConcurrencyIT} et passent par Failsafe.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NumerotationServiceTests.MutableClockConfig.class)
class NumerotationServiceTests {

    /**
     * Configuration de test qui remplace le {@link Clock} par defaut par un
     * proxy mutable. Tous les tests partagent la meme reference, ce qui
     * permet a un test de modifier l'horloge sans casser les autres (chaque
     * test reset l'horloge dans {@link #setUp()}).
     */
    @TestConfiguration
    static class MutableClockConfig {
        static final AtomicReference<Clock> CLOCK_REF =
                new AtomicReference<>(Clock.systemDefaultZone());

        @Bean
        @Primary
        public Clock testClock() {
            // Delegation lazy : a chaque appel, recupere le Clock courant de la ref.
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
    private NumerotationService numerotationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private Long hotelMrId;
    private Long hotelFrId;
    private int currentYear;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        // Reset horloge systeme (un test peut la deplacer)
        MutableClockConfig.CLOCK_REF.set(Clock.systemDefaultZone());
        currentYear = LocalDate.now().getYear();

        // Cleanup avant seed pour eviter contamination entre tests (le contexte
        // Spring est reutilise -> H2 est partagee).
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.hotels");

        // Seed 2 hotels avec des codePays distincts pour valider l'isolation.
        // Pas besoin de TenantContext ici : Hotel n'est pas une entite tenant.
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
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.hotels");
        MutableClockConfig.CLOCK_REF.set(Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("T1 - premier appel next(FACT) hotel MR -> FACT-{annee}-MR-000001")
    void shouldReturnFirstNumber() {
        TenantContext.set(hotelMrId);

        String numero = transactionTemplate.execute(status ->
                numerotationService.next(TypeNumerotation.FACT));

        assertEquals(String.format("FACT-%d-MR-000001", currentYear), numero);
    }

    @Test
    @DisplayName("T2 - 3 appels consecutifs next(FACT) -> 000001, 000002, 000003")
    void shouldIncrementSequentially() {
        TenantContext.set(hotelMrId);

        String n1 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        String n2 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        String n3 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));

        assertEquals(String.format("FACT-%d-MR-000001", currentYear), n1);
        assertEquals(String.format("FACT-%d-MR-000002", currentYear), n2);
        assertEquals(String.format("FACT-%d-MR-000003", currentYear), n3);
    }

    @Test
    @DisplayName("T3 - sequences independantes par type : FACT et PAY chacun a 000001")
    void shouldKeepSequencesIndependentByType() {
        TenantContext.set(hotelMrId);

        String fact = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        String pay = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.PAY));

        assertEquals(String.format("FACT-%d-MR-000001", currentYear), fact);
        assertEquals(String.format("PAY-%d-MR-000001", currentYear), pay);
    }

    @Test
    @DisplayName("T4 - sequences independantes par hotel (isolation tenant via TenantContext)")
    void shouldKeepSequencesIndependentByHotel() {
        TenantContext.set(hotelMrId);
        String mr1 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        String mr2 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        TenantContext.clear();

        TenantContext.set(hotelFrId);
        String fr1 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));

        assertEquals(String.format("FACT-%d-MR-000001", currentYear), mr1);
        assertEquals(String.format("FACT-%d-MR-000002", currentYear), mr2);
        // Hotel FR doit demarrer a 000001, sans voir le compteur du hotel MR,
        // et avec son propre code pays FR.
        assertEquals(String.format("FACT-%d-FR-000001", currentYear), fr1);
    }

    @Test
    @DisplayName("T5 - sans TenantContext -> @RequireTenant rejette avec error.tenant.missing")
    void shouldRejectWhenTenantAbsent() {
        // Pas de TenantContext.set(...) avant l'appel.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT)));
        // Le message vient de RequireTenantAspect (cas standard).
        assertEquals("error.tenant.missing", ex.getMessage());
    }

    @Test
    @DisplayName("T6 - changement d'exercice (mock Clock) -> reset a 000001 dans la nouvelle annee")
    void shouldResetSequenceOnYearChange() {
        TenantContext.set(hotelMrId);

        // Annee N : 2 emissions
        MutableClockConfig.CLOCK_REF.set(fixedClockOnJanuaryFirst(2026));
        String n1 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));
        String n2 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));

        // Annee N+1 : on attend 000001 (reset par exercice)
        MutableClockConfig.CLOCK_REF.set(fixedClockOnJanuaryFirst(2027));
        String n3 = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.FACT));

        assertEquals("FACT-2026-MR-000001", n1);
        assertEquals("FACT-2026-MR-000002", n2);
        assertEquals("FACT-2027-MR-000001", n3);
    }

    @Test
    @DisplayName("T7 - format strict : {TYPE}-{exercice}-{codePays}-{6 chiffres zero-paddes}")
    void shouldRespectStrictFormat() {
        TenantContext.set(hotelFrId);
        // Force l'annee a 2026 pour un assert deterministe.
        MutableClockConfig.CLOCK_REF.set(fixedClockOnJanuaryFirst(2026));

        String numero = transactionTemplate.execute(s -> numerotationService.next(TypeNumerotation.BC));

        assertEquals("BC-2026-FR-000001", numero);
        // Sanity : separateurs et nombre de groupes
        String[] parts = numero.split("-");
        assertEquals(4, parts.length, "Format attendu : 4 segments separes par '-'");
        assertEquals("BC", parts[0]);
        assertEquals("2026", parts[1]);
        assertEquals("FR", parts[2]);
        assertEquals(6, parts[3].length(), "Le compteur doit etre zero-padde sur 6 chiffres");
        assertTrue(parts[3].chars().allMatch(Character::isDigit), "Le compteur ne doit contenir que des chiffres");
    }

    /**
     * Construit un Clock fige au 1er janvier midi UTC de l'annee donnee.
     * Midi UTC garantit qu'on reste dans la meme journee quel que soit le
     * fuseau d'execution (Africa/Nouakchott = UTC+0, sans DST).
     */
    private static Clock fixedClockOnJanuaryFirst(int year) {
        Instant instant = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("UTC"))
                .plusHours(12).toInstant();
        return Clock.fixed(instant, ZoneId.of("UTC"));
    }
}
