package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test d'integration Failsafe : concurrence reelle de creation de factures sur
 * PostgreSQL via Testcontainers (Tour 22.1).
 *
 * <p>Pattern identique a {@link NumerotationServiceConcurrencyIT} (Tour 6A) :
 * gestion manuelle du container Postgres pour pouvoir checker
 * {@code DockerClientFactory.isDockerAvailable()} dans {@code @BeforeAll}
 * et faire un {@link Assumptions#assumeTrue} avant tout appel a la creation
 * du container. Sinon l'extension {@code @Testcontainers} leverait une
 * {@code IllegalStateException} fatale qui casserait
 * {@code mvnw verify} sur les postes sans Docker.</p>
 *
 * <h3>Cas teste TC1 - 100 threads x 10 factures = 1000 factures</h3>
 * <p>Verifie qu'avec 100 threads qui creent chacun 10 factures sur le meme
 * hotel, on obtient :
 * <ul>
 *   <li>1000 numeros de facture distincts (aucun doublon),</li>
 *   <li>la sequence complete 000001..001000 (aucun trou),</li>
 *   <li>aucune exception (pas de deadlock, pas de violation de contrainte
 *       {@code uk_factures_hotel_numero}).</li>
 * </ul>
 *
 * <p>Le {@code NumerotationService} pose un verrou pessimiste sur la table
 * {@code finance.numerotation_sequence} ; la creation de facture serialise
 * donc elle aussi via ce point unique.</p>
 *
 * <h3>Performance & duree</h3>
 * <p>1000 factures x ~3 round-trips DB par numero + INSERT facture. Sur poste
 * dev ~30-90s (premier run plus long pour pull image). Timeout fixe a 120s.</p>
 *
 * <h3>Skip si Docker indisponible</h3>
 * <p>Voir {@link NumerotationServiceConcurrencyIT} pour les details. Les
 * postes CI sans demon Docker skippent proprement via {@code assumeTrue}.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "spring.profiles.active="
})
class FactureNumerotationConcurrencyIT {

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
                "Docker indisponible sur cet environnement : test de concurrence skippe. "
                        + "Pour l'executer en local, demarrer Docker Desktop ; en CI, fournir un demon Docker.");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.datasource.driver-class-name", () -> postgres.getDriverClassName());
    }

    @Autowired
    private FactureService factureService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long hotelId;
    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        // Cleanup brut. Les tables finance/dbusers/hotels du test isole.
        // Ordre FK strict.
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");

        // Insere un hotel de test ; le code doit etre unique vs seeds Liquibase.
        jdbcTemplate.update(
                "INSERT INTO core.hotels(hotel_code, hotel_nom, code_pays, devise, fuseau_horaire, actif) "
                        + "VALUES ('FACTIT', 'Test Facture Concurrency Hotel', 'MR', 'MRU', 'Africa/Nouakchott', true)");
        hotelId = jdbcTemplate.queryForObject(
                "SELECT hotel_id FROM core.hotels WHERE hotel_code = 'FACTIT'", Long.class);

        // Role + user (pour passer le currentUserId() de FactureServiceImpl).
        // ON CONFLICT DO NOTHING : 'GERANT' peut deja exister via seeds Liquibase 011.
        jdbcTemplate.update(
                "INSERT INTO core.roles(role_code, role_nom, actif) VALUES ('GERANT', 'Gerant', true) "
                        + "ON CONFLICT (role_code) DO NOTHING");
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM core.roles WHERE role_code = 'GERANT'", Long.class);

        jdbcTemplate.update(
                "INSERT INTO core.dbusers(username, email, password_hash, prenom, nom, "
                        + "hotel_id, role_id, actif, compte_verrouille) "
                        + "VALUES ('factit-user', 'factit@test.local', '$2a$12$placeholder', "
                        + "'Test', 'Concurrency', ?, ?, true, false)",
                hotelId, roleId);
        userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM core.dbusers WHERE username = 'factit-user'", Long.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures WHERE hotel_id = ?", hotelId);
        jdbcTemplate.update("DELETE FROM finance.comptes WHERE hotel_id = ?", hotelId);
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence WHERE hotel_id = ?", hotelId);
        jdbcTemplate.update("DELETE FROM core.dbusers WHERE username = 'factit-user'");
        jdbcTemplate.update("DELETE FROM core.hotels WHERE hotel_code = 'FACTIT'");
    }

    /**
     * Authentifie le thread courant comme l'user de test. Necessaire car
     * {@link FactureServiceImpl#currentUserId()} levee
     * {@code BusinessException("error.user.unknown")} sinon.
     */
    private void authenticateThread() {
        UserPrincipal principal = new UserPrincipal(
                userId, "factit-user", "factit@test.local", "pwd",
                "Test", "Concurrency", hotelId, "FACTIT", "Test Facture Concurrency Hotel",
                "GERANT", "GERANT", Boolean.TRUE, Boolean.FALSE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("TC1 - 100 threads x 10 factures = 1000 numeros distincts, sequence sans trou")
    void shouldGenerateOneThousandSequentialFactureNumbersUnderConcurrency() throws InterruptedException {
        final int N_THREADS = 100;
        final int FACTURES_PER_THREAD = 10;
        final int TOTAL = N_THREADS * FACTURES_PER_THREAD;

        Set<String> numerosCollected = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(N_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N_THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);

        for (int t = 0; t < N_THREADS; t++) {
            pool.submit(() -> {
                try {
                    // ThreadLocal n'herite pas : chaque thread doit re-poser le tenant + auth.
                    TenantContext.set(hotelId);
                    authenticateThread();
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Latch start jamais leve");
                    }
                    for (int i = 0; i < FACTURES_PER_THREAD; i++) {
                        // Facture minimale, sans clientId ni lignes : focalise le test
                        // sur la seule numerotation. clientId null -> pas d'operation
                        // comptable auxiliaire (court-circuit Tour 22.1, normal).
                        FactureCreateDto dto = new FactureCreateDto(
                                null, null, null, null, null, null,
                                null, null, "MRU", null, List.of());
                        FactureDto created = factureService.create(dto);
                        numerosCollected.add(created.numeroFacture());
                    }
                } catch (Throwable th) {
                    firstFailure.compareAndSet(null, th);
                } finally {
                    TenantContext.clear();
                    SecurityContextHolder.clearContext();
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(15, TimeUnit.SECONDS),
                "Tous les threads n'ont pas ete prets a temps");
        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS),
                "Timeout 120s : la concurrence est trop lente ou il y a un deadlock");
        pool.shutdownNow();

        assertNull(firstFailure.get(), () -> "Echec dans un thread : " + firstFailure.get());

        // Verification 1 : 1000 numeros distincts collectes (Set rejette doublons)
        assertEquals(TOTAL, numerosCollected.size(),
                "On attend " + TOTAL + " numeros distincts, observe : " + numerosCollected.size());

        // Verification 2 : sequence sans trou 000001..001000
        Set<Integer> sequences = numerosCollected.stream()
                .map(s -> Integer.parseInt(s.substring(s.lastIndexOf('-') + 1)))
                .collect(Collectors.toSet());
        Set<Integer> expected = IntStream.rangeClosed(1, TOTAL).boxed().collect(Collectors.toSet());
        assertEquals(expected, sequences,
                "Sequence 1.." + TOTAL + " sans trou ni doublon attendue");
    }

}
