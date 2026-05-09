package com.cityprojects.citybackend.common.tenant;

import com.cityprojects.testfixtures.tenant._TestTenantEntity;
import com.cityprojects.testfixtures.tenant._TestTenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration du multi-tenancy Hibernate 6 par DISCRIMINATOR
 * (Tour 3B Alt-NEW-6 + finalisation Option A + garde).
 * <p>
 * Valide que :
 * <ol>
 *   <li>{@link TenantContext#set(Long)} a 1L : seules les entites
 *       {@code hotelId = 1} sont retournees (filtre Hibernate auto sur
 *       {@code @TenantId}).</li>
 *   <li>Idem pour le tenant 2.</li>
 *   <li>Sans tenant (TenantContext vide) : le resolver retourne le sentinel
 *       {@link CityTenantIdentifierResolver#ROOT} (= 0L) et
 *       {@link CityTenantIdentifierResolver#isRoot(Long)} renvoie {@code true}.
 *       Hibernate <b>bypass alors le filtre de tenant</b> et renvoie TOUTES
 *       les lignes (3 au total). C'est l'<i>Option A</i> : la securite metier
 *       est portee par {@link RequireTenant} / {@link RequireTenantAspect} et
 *       non plus par un deny implicite a la couche Hibernate.</li>
 *   <li>Le {@link TenantContext} reste isole entre threads concurrents.</li>
 * </ol>
 *
 * <h3>Strategies cles</h3>
 * <ul>
 *   <li><b>Seed</b> : avec {@code @TenantId}, Hibernate populate la colonne
 *       discriminator a l'INSERT en appelant le {@link CityTenantIdentifierResolver}
 *       — la valeur passee dans l'entite via {@code setHotelId} est ignoree.
 *       On doit donc {@code TenantContext.set(N)} <b>avant</b> chaque
 *       transaction d'insert. Pour seedrer plusieurs tenants, on emet
 *       plusieurs transactions courtes.</li>
 *   <li><b>Pas de {@code @Transactional} sur les methodes de test</b> :
 *       on utilise {@link TransactionTemplate} pour controler precisement
 *       le moment ou la session Hibernate est ouverte (le resolver est
 *       appele a ce moment-la).</li>
 *   <li><b>Cleanup brut SQL</b> dans {@link #tearDown()} via {@link JdbcTemplate}
 *       : un {@code repository.deleteAll()} passerait par Hibernate. Avec Option A,
 *       en l'absence de tenant le resolver renvoie ROOT et Hibernate bypass —
 *       donc {@code deleteAll()} effacerait bien tout, mais on garde le DELETE
 *       SQL natif pour rester independant du resolver et des bugs futurs.</li>
 *   <li><b>Nom de classe en {@code *IT}</b> : convention Failsafe (goal
 *       {@code mvnw verify}). Ce test demarre Spring + H2 + JPA = vrai
 *       integration test, pas un unitaire.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@EntityScan(basePackages = {
        "com.cityprojects.citybackend",
        "com.cityprojects.testfixtures.tenant"
})
@EnableJpaRepositories(basePackages = {
        "com.cityprojects.citybackend",
        "com.cityprojects.testfixtures.tenant"
})
class TenantMultiTenancyIT {

    @Autowired
    private _TestTenantRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // Securite : pas de tenant residuel d'un test precedent.
        TenantContext.clear();
        // Cleanup brut SQL avant seed (la base H2 est partagee par les tests
        // de la meme classe a cause du context Spring reutilise).
        jdbcTemplate.update("DELETE FROM test_tenant_entity");
        seed();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Cleanup SQL natif : independant du comportement du resolver.
        jdbcTemplate.update("DELETE FROM test_tenant_entity");
    }

    /**
     * Seed : 2 entites pour hotel 1, 1 entite pour hotel 2. Chaque tenant
     * est positionne <b>avant</b> sa propre transaction pour que Hibernate
     * appelle le resolver avec la bonne valeur a l'INSERT.
     */
    private void seed() {
        TenantContext.set(1L);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                repository.save(new _TestTenantEntity("h1-a", 1L));
                repository.save(new _TestTenantEntity("h1-b", 1L));
            });
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(2L);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                repository.save(new _TestTenantEntity("h2-a", 2L));
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - TenantContext.set(1) : seules les entites hotelId=1 sont retournees")
    void shouldFilterByHotel1() {
        TenantContext.set(1L);

        List<_TestTenantEntity> result = transactionTemplate.execute(status -> repository.findAll());

        assertNotNull(result);
        assertEquals(2, result.size(), "On attend 2 entites pour hotelId=1");
        assertTrue(result.stream().allMatch(e -> e.getHotelId() == 1L),
                "Toutes les entites retournees doivent appartenir au hotel 1");
    }

    @Test
    @DisplayName("T2 - TenantContext.set(2) : seule l'entite hotelId=2 est retournee")
    void shouldFilterByHotel2() {
        TenantContext.set(2L);

        List<_TestTenantEntity> result = transactionTemplate.execute(status -> repository.findAll());

        assertNotNull(result);
        assertEquals(1, result.size(), "On attend 1 entite pour hotelId=2");
        assertEquals(2L, result.get(0).getHotelId());
    }

    @Test
    @DisplayName("T3 - Sans tenant (Option A) : resolver retourne ROOT, Hibernate bypass le filtre, toutes les lignes sont visibles")
    void shouldBypassFilterWhenTenantAbsent() {
        // Option A : quand TenantContext est vide, le resolver retourne le sentinel
        // CityTenantIdentifierResolver.ROOT (= 0L) et isRoot(ROOT) renvoie true.
        // Hibernate detecte alors le mode "root tenant" et OMET le predicat
        // WHERE hotel_id = ? : la requete devient un SELECT non filtre. Toutes les
        // lignes (2 du hotel 1 + 1 du hotel 2 = 3) sont donc visibles.
        //
        // Cette permissivite cote ORM est intentionnelle : elle autorise les
        // services techniques / globaux (admin, schedulers, exports cross-tenant)
        // a operer sans contexte. La protection des services METIER contre l'absence
        // de tenant est portee par @RequireTenant + RequireTenantAspect (cf. les
        // tests RequireTenantAspectTests).
        TenantContext.clear();

        List<_TestTenantEntity> result = transactionTemplate.execute(status -> repository.findAll());

        assertNotNull(result);
        assertEquals(3, result.size(),
                "Sans tenant (ROOT), Hibernate doit bypasser le filtre et renvoyer toutes les lignes");
    }

    @Test
    @DisplayName("T4 - Isolation entre threads concurrents")
    void shouldIsolateBetweenThreads() throws InterruptedException {
        AtomicReference<Integer> threadACount = new AtomicReference<>();
        AtomicReference<Integer> threadBCount = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            try {
                TenantContext.set(1L);
                ready.countDown();
                start.await();
                List<_TestTenantEntity> r = transactionTemplate.execute(s -> repository.findAll());
                threadACount.set(r != null ? r.size() : -1);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        }, "tenant-thread-A");

        Thread threadB = new Thread(() -> {
            try {
                TenantContext.set(2L);
                ready.countDown();
                start.await();
                List<_TestTenantEntity> r = transactionTemplate.execute(s -> repository.findAll());
                threadBCount.set(r != null ? r.size() : -1);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        }, "tenant-thread-B");

        threadA.start();
        threadB.start();

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Les threads n'ont pas ete prets a temps");
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Les threads n'ont pas termine a temps");

        if (failure.get() != null) {
            throw new AssertionError("Echec dans un thread", failure.get());
        }
        assertEquals(2, threadACount.get(), "Thread A (hotel 1) doit voir 2 entites");
        assertEquals(1, threadBCount.get(), "Thread B (hotel 2) doit voir 1 entite");
    }
}
