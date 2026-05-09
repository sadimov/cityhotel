package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test d'integration Failsafe : concurrence reelle sur PostgreSQL via Testcontainers.
 * <p>
 * Verifie qu'avec 10 threads qui appellent simultanement {@code next(FACT)} sur
 * le meme hotel, on obtient :
 * <ul>
 *   <li>10 numeros distincts (aucun doublon),</li>
 *   <li>la sequence complete 000001..000010 (aucun trou),</li>
 *   <li>aucune exception (pas de deadlock, pas de violation de contrainte).</li>
 * </ul>
 *
 * <h3>Pourquoi Postgres et non H2 pour ce test</h3>
 * <p>H2 supporte {@code PESSIMISTIC_WRITE} mais sa serialisation des
 * transactions concurrentes en mode {@code MODE=PostgreSQL} differe parfois
 * subtilement de Postgres reel (notamment sur l'ordre de wakeup). Pour valider
 * que la garantie d'unicite tient en production (Postgres 18 cible), on
 * utilise l'image officielle.</p>
 *
 * <h3>Performance & duree</h3>
 * <p>Premier run : ~30-60s (pull image + boot Postgres + Liquibase). Runs
 * suivants (image en cache) : ~10-15s. Avec 10 threads serialises par le
 * verrou pessimiste, l'ensemble doit terminer en quelques secondes.</p>
 *
 * <h3>Skip si Docker indisponible</h3>
 * <p>On gere le container manuellement (pas l'extension {@code @Testcontainers})
 * pour pouvoir checker {@code DockerClientFactory.isDockerAvailable()} dans
 * {@code @BeforeAll} et faire un {@link Assumptions#assumeTrue} avant tout
 * appel a la creation du container. Sinon, l'extension {@code @Testcontainers}
 * leve une {@code IllegalStateException} fatale a l'init du test, ce qui
 * casse {@code mvnw verify} sur les postes sans Docker (devs CI sans demon).</p>
 *
 * <h3>Configuration Spring</h3>
 * <p>{@link DynamicPropertySource} cable le DataSource sur le container
 * (alternative legere a {@code @ServiceConnection} qui exigerait
 * {@code spring-boot-testcontainers} non present au pom). Liquibase est
 * active explicitement (le profil "test" le desactive pour H2).</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        // pas de profil "test" actif : on revient sur application.yml +
        // overrides ci-dessous + DataSource pointe sur le container.
        "spring.profiles.active="
})
class NumerotationServiceConcurrencyIT {

    /**
     * Container declare lazy : initialise dans {@link #requireDockerAndStartContainer()}
     * SI ET SEULEMENT SI Docker est disponible. Si on l'avait declare en
     * initialiseur de champ statique, sa construction echouerait avant tout
     * {@code @BeforeAll}, court-circuitant {@link Assumptions#assumeTrue}.
     */
    private static PostgreSQLContainer<?> postgres;

    /**
     * Skippe la classe entiere si Docker n'est pas disponible. Demarre le
     * container Postgres manuellement quand il l'est. Le container est demarre
     * AVANT le bootstrap Spring car {@link DynamicPropertySource} est appele
     * a la creation de l'ApplicationContext, qui survient apres @BeforeAll.
     */
    @BeforeAll
    static void requireDockerAndStartContainer() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            // En l'absence totale de Docker (poste sans Docker Desktop installe),
            // l'instance throw immediatement. On retombe sur false et on skippe.
            dockerAvailable = false;
        }
        assumeTrue(dockerAvailable,
                "Docker indisponible sur cet environnement : test de concurrence skippe. "
                        + "Pour l'executer en local, demarrer Docker Desktop ; en CI, fournir un demon Docker.");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        // Suppliers lazy : Spring les appelle apres @BeforeAll, donc apres start().
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.datasource.driver-class-name", () -> postgres.getDriverClassName());
    }

    @Autowired
    private NumerotationService numerotationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long hotelId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        // Cleanup brut (preserve les donnees seedees par Liquibase 014-superadmin)
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        // Insere un hotel de test ; le code doit etre unique vs seeds Liquibase.
        jdbcTemplate.update(
                "INSERT INTO core.hotels(hotel_code, hotel_nom, code_pays, devise, fuseau_horaire, actif) "
                        + "VALUES ('ITTEST', 'Test Concurrency Hotel', 'MR', 'MRU', 'Africa/Nouakchott', true)");
        hotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM core.hotels WHERE hotel_code = 'ITTEST'", Long.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence WHERE hotel_id = ?", hotelId);
        jdbcTemplate.update("DELETE FROM core.hotels WHERE hotel_code = 'ITTEST'");
    }

    @Test
    @DisplayName("TC1 - 10 threads concurrents sur le meme hotel : 10 numeros distincts, sequence sans trou")
    void shouldProduceUniqueSequentialNumbersUnderConcurrency() throws InterruptedException {
        final int threadCount = 10;
        Set<String> generatedNumbers = new ConcurrentSkipListSet<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // ThreadLocal n'herite pas : chaque thread doit re-poser le tenant.
                    TenantContext.set(hotelId);
                    ready.countDown();
                    // Attend le go pour maximiser la concurrence reelle (sinon le 1er
                    // thread peut etre deja parti quand le 10e arrive).
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Latch start jamais leve");
                    }
                    String numero = numerotationService.next(TypeNumerotation.FACT);
                    generatedNumbers.add(numero);
                } catch (Throwable t) {
                    firstFailure.compareAndSet(null, t);
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }

        // Attend que tous les threads soient prets, puis lance.
        assertTrue(ready.await(10, TimeUnit.SECONDS), "Tous les threads n'ont pas ete prets a temps");
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "Les threads n'ont pas termine a temps (deadlock?)");
        executor.shutdownNow();

        assertNull(firstFailure.get(), () -> "Echec dans un thread : " + firstFailure.get());

        // 10 numeros distincts collectes (le Set rejette les doublons)
        assertEquals(threadCount, generatedNumbers.size(),
                "On attend " + threadCount + " numeros distincts, observe : " + generatedNumbers);

        // Sequence sans trou : les suffixes 000001..000010 doivent tous etre presents.
        int year = java.time.LocalDate.now().getYear();
        for (int i = 1; i <= threadCount; i++) {
            String expected = String.format("FACT-%d-MR-%06d", year, i);
            assertTrue(generatedNumbers.contains(expected),
                    "Numero manquant : " + expected + " (set : " + generatedNumbers + ")");
        }
    }
}
