package com.cityprojects.citybackend.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de {@link RequireTenantAspect}.
 * <p>
 * Approche : <b>{@link AspectJProxyFactory}</b> plutot que {@code @SpringBootTest}.
 * Pas de boot Spring, pas de SessionFactory, pas de H2 — un simple proxy AspectJ
 * sur une classe POJO de test, ce qui rend ces tests rapides (< 50 ms chacun)
 * et reellement <b>unitaires</b>. Ils peuvent etre executes par Surefire au goal
 * {@code test} (nom en {@code *Tests}), distinct du goal {@code verify} qui porte
 * les tests d'integration {@code *IT}.
 *
 * <p>Couverture :
 * <ul>
 *   <li>T5 : pointcut {@code @within} (annotation classe) sans tenant -&gt;
 *       {@link IllegalStateException} avec message
 *       {@link TenantContext#ERROR_TENANT_MISSING}.</li>
 *   <li>T6 : pointcut {@code @within} (annotation classe) avec tenant -&gt; passe.</li>
 *   <li>T7 : pointcut {@code @annotation} (annotation methode) sans tenant -&gt; refuse,
 *       avec tenant -&gt; passe.</li>
 *   <li>T8 : annotation simultanee classe + methode -&gt; idempotence et resultat
 *       final correct (l'aspect est before, pas around : tirer 2x ne change rien).</li>
 * </ul>
 */
class RequireTenantAspectTests {

    /**
     * Cible POJO : une classe annotee au niveau type pour valider que le
     * pointcut {@code @within} est bien evalue. Aucune dependance Spring.
     */
    @RequireTenant
    static class TenantSensitiveService {
        String doWork() {
            return "ok";
        }
    }

    /**
     * Cible POJO : pas d'annotation au niveau classe, annotation au niveau
     * methode pour valider le pointcut {@code @annotation}.
     */
    static class MethodAnnotatedService {
        @RequireTenant
        String doGuardedWork() {
            return "guarded";
        }

        String doFreeWork() {
            return "free";
        }
    }

    /**
     * Cible POJO : annotation au niveau classe ET methode pour valider qu'il
     * n'y a pas d'effet de bord (l'aspect est before, idempotent).
     */
    @RequireTenant
    static class DoubleAnnotatedService {
        @RequireTenant
        String doDoubleGuardedWork() {
            return "double-ok";
        }
    }

    private TenantSensitiveService classProxied;
    private MethodAnnotatedService methodProxied;
    private DoubleAnnotatedService doubleProxied;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        AspectJProxyFactory classFactory = new AspectJProxyFactory(new TenantSensitiveService());
        classFactory.addAspect(new RequireTenantAspect());
        this.classProxied = classFactory.getProxy();

        AspectJProxyFactory methodFactory = new AspectJProxyFactory(new MethodAnnotatedService());
        methodFactory.addAspect(new RequireTenantAspect());
        this.methodProxied = methodFactory.getProxy();

        AspectJProxyFactory doubleFactory = new AspectJProxyFactory(new DoubleAnnotatedService());
        doubleFactory.addAspect(new RequireTenantAspect());
        this.doubleProxied = doubleFactory.getProxy();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T5 - @within sans TenantContext.set : -> IllegalStateException(error.tenant.missing)")
    void shouldRejectWhenTenantAbsent() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> classProxied.doWork(),
                "L'aspect doit refuser l'invocation quand TenantContext est vide");
        assertEquals(TenantContext.ERROR_TENANT_MISSING, ex.getMessage());
    }

    @Test
    @DisplayName("T6 - @within avec TenantContext.set(1L) : passe sans exception")
    void shouldPassWhenTenantPresent() {
        TenantContext.set(1L);

        String result = assertDoesNotThrow(() -> classProxied.doWork(),
                "L'aspect doit laisser passer l'invocation quand un tenant est positionne");
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("T7 - @annotation (methode) sans tenant -> refuse ; avec tenant -> passe")
    void shouldGuardAtMethodLevelToo() {
        // Sans tenant : la methode annotee echoue.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> methodProxied.doGuardedWork(),
                "Le pointcut @annotation doit declencher l'aspect au niveau methode");
        assertEquals(TenantContext.ERROR_TENANT_MISSING, ex.getMessage());

        // Methode SANS annotation : doit passer meme sans tenant (preuve que
        // l'aspect ne tire pas globalement sur la classe).
        String free = assertDoesNotThrow(() -> methodProxied.doFreeWork(),
                "Methode non annotee : aucune garde, meme sans tenant");
        assertEquals("free", free);

        // Avec tenant : la methode annotee passe.
        TenantContext.set(2L);
        String guarded = assertDoesNotThrow(() -> methodProxied.doGuardedWork(),
                "Avec tenant positionne, la methode annotee doit s'executer");
        assertEquals("guarded", guarded);
    }

    @Test
    @DisplayName("T8 - annotation classe + methode : idempotent, resultat final correct")
    void shouldNotDoubleFireWhenAnnotatedAtBothLevels() {
        // Sans tenant : refus (le premier des deux pointcuts qui matche suffit
        // — pas de double exception, juste une).
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> doubleProxied.doDoubleGuardedWork());
        assertEquals(TenantContext.ERROR_TENANT_MISSING, ex.getMessage());

        // Avec tenant : passe quel que soit le nombre de fois ou l'aspect tire
        // (l'aspect est @Before avec une garde idempotente).
        TenantContext.set(3L);
        String result = assertDoesNotThrow(() -> doubleProxied.doDoubleGuardedWork());
        assertEquals("double-ok", result);
    }
}
