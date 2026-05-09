package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test d'integration Failsafe : concurrence reelle de la creation de clients
 * sur PostgreSQL via Testcontainers (Tour 10.1).
 * <p>
 * 10 threads creent simultanement un client sur le meme hotel ; on attend :
 * <ul>
 *   <li>10 numeros {@code CLI-{annee}-MR-000001..000010} distincts (aucun doublon),</li>
 *   <li>la sequence complete sans trou,</li>
 *   <li>aucune exception (pas de deadlock, pas de violation de contrainte
 *       {@code uk_clients_hotel_numero}).</li>
 * </ul>
 *
 * <h3>Pourquoi un IT separe alors qu'on a deja {@link com.cityprojects.citybackend.service.finance.NumerotationServiceConcurrencyIT}</h3>
 * <p>{@code NumerotationServiceConcurrencyIT} valide le service de numerotation
 * en isolation. Ici on valide la chaine COMPLETE
 * {@code ClientService.create -> NumerotationService.next -> repo.save} avec
 * trois aspects supplementaires :</p>
 * <ul>
 *   <li>la transaction du service englobe l'incrementation de la sequence ET
 *       l'INSERT du client ; un deadlock different peut emerger,</li>
 *   <li>la contrainte {@code UNIQUE(hotel_id, numero_client)} est touchee a
 *       chaque INSERT — un trou dans la sequence de numerotation generale (FACT
 *       vs CLI partagent la meme table) ne se voit qu'ici,</li>
 *   <li>le mapper / les hooks d'audit (createdBy/updatedAt) sont sur le chemin
 *       chaud, et un bug de propagation tenant via {@code TenantContext} dans
 *       l'ExecutorService se materialiserait par un {@code error.tenant.missing}
 *       dans un thread.</li>
 * </ul>
 *
 * <h3>Skip si Docker indisponible</h3>
 * <p>Pattern identique a {@link com.cityprojects.citybackend.service.finance.NumerotationServiceConcurrencyIT}
 * : container declare lazy en {@code @BeforeAll} apres
 * {@link Assumptions#assumeTrue} sur la dispo Docker. Sans cela, l'extension
 * {@code @Testcontainers} casse {@code mvnw verify} sur les postes sans
 * demon Docker.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        // Pas de profil "test" : on s'aligne sur application.yml + Liquibase reel
        // pour avoir les schemas et seeds (roles, sequences) tels qu'en prod.
        "spring.profiles.active="
})
class ClientServiceConcurrencyIT {

    /**
     * Container declare lazy : initialise dans
     * {@link #requireDockerAndStartContainer()} SI ET SEULEMENT SI Docker est
     * disponible. Si on l'avait declare en initialiseur de champ statique, sa
     * construction echouerait avant tout {@code @BeforeAll}, court-circuitant
     * {@link Assumptions#assumeTrue}.
     */
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void requireDockerAndStartContainer() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        assumeTrue(dockerAvailable,
                "Docker indisponible sur cet environnement : test de concurrence client skippe.");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.datasource.driver-class-name", () -> postgres.getDriverClassName());
    }

    @Autowired
    private ClientService clientService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long hotelId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        // Cleanup propre : on ne touche pas aux seeds Liquibase (roles, superadmin),
        // on supprime uniquement nos donnees de test.
        jdbcTemplate.update("DELETE FROM client.clients WHERE numero_client LIKE 'CLI-%-MR-%'");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence WHERE type = 'CLI'");
        jdbcTemplate.update("DELETE FROM core.hotels WHERE hotel_code = 'ITCLI'");
        jdbcTemplate.update(
                "INSERT INTO core.hotels(hotel_code, hotel_nom, code_pays, devise, fuseau_horaire, actif) "
                        + "VALUES ('ITCLI', 'IT Concurrency Hotel Client', 'MR', 'MRU', 'Africa/Nouakchott', true)");
        hotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM core.hotels WHERE hotel_code = 'ITCLI'", Long.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (hotelId != null) {
            jdbcTemplate.update("DELETE FROM client.clients WHERE hotel_id = ?", hotelId);
            jdbcTemplate.update("DELETE FROM finance.numerotation_sequence WHERE hotel_id = ?", hotelId);
            jdbcTemplate.update("DELETE FROM core.hotels WHERE hotel_id = ?", hotelId);
        }
    }

    @Test
    @DisplayName("TC1 - 10 threads concurrents creent un client : 10 numeros CLI distincts, sequence 000001..000010 sans trou")
    void shouldGenerateDistinctClientNumbersUnderConcurrency() throws InterruptedException {
        final int threadCount = 10;
        Set<String> numerosClient = new ConcurrentSkipListSet<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    // ThreadLocal ne s'herite pas : chaque thread doit poser le tenant.
                    TenantContext.set(hotelId);
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Latch start jamais leve");
                    }
                    ClientCreateDto dto = new ClientCreateDto(
                            "Prenom" + idx, "Nom" + idx,
                            null, null, null, null, null, null,
                            null, null, null, null);
                    ClientDto created = clientService.create(dto);
                    numerosClient.add(created.numeroClient());
                } catch (Throwable t) {
                    firstFailure.compareAndSet(null, t);
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS),
                "Tous les threads n'ont pas ete prets a temps");
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS),
                "Les threads n'ont pas termine a temps (deadlock?)");
        executor.shutdownNow();

        assertNull(firstFailure.get(),
                () -> "Echec dans un thread : " + firstFailure.get());

        // 10 numeros distincts (Set rejette les doublons)
        assertEquals(threadCount, numerosClient.size(),
                "On attend " + threadCount + " numeros distincts, observe : " + numerosClient);

        // Sequence sans trou : suffixes 000001..000010 tous presents.
        int year = java.time.LocalDate.now().getYear();
        Set<String> expected = IntStream.rangeClosed(1, threadCount)
                .mapToObj(i -> String.format("CLI-%d-MR-%06d", year, i))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (String exp : expected) {
            assertTrue(numerosClient.contains(exp),
                    "Numero manquant : " + exp + " (set : " + numerosClient + ")");
        }
    }
}
