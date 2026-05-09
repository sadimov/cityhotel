package com.cityprojects.citybackend.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de {@link TenantContext}.
 * Couvre : set/get nominal, contrat null, contrat sentinel 0L/negatif,
 * isolation entre threads, idempotence apres clear.
 */
class TenantContextTest {

    @AfterEach
    void cleanUp() {
        // Securise : meme si un test oublie clear(), on laisse l'environnement propre.
        TenantContext.clear();
    }

    @Test
    @DisplayName("set puis get retourne la valeur positionnee")
    void shouldReturnValueAfterSet() {
        TenantContext.set(42L);

        assertTrue(TenantContext.isSet());
        assertEquals(42L, TenantContext.get());
        assertEquals(42L, TenantContext.getOrNull());
    }

    @Test
    @DisplayName("clear remet le contexte a null et get leve IllegalStateException")
    void shouldClearContext() {
        TenantContext.set(7L);

        TenantContext.clear();

        assertFalse(TenantContext.isSet());
        assertNull(TenantContext.getOrNull());
        IllegalStateException ex = assertThrows(IllegalStateException.class, TenantContext::get);
        assertEquals(TenantContext.ERROR_TENANT_MISSING, ex.getMessage());
    }

    @Test
    @DisplayName("set(null) leve IllegalArgumentException (message technique) et ne pollue pas le contexte")
    void shouldRejectNullHotelId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TenantContext.set(null));
        // Message technique destine aux logs et au debug developpeur, pas une cle i18n.
        assertEquals("hotelId must not be null", ex.getMessage());

        // Le contexte doit rester vierge.
        assertFalse(TenantContext.isSet());
        assertNull(TenantContext.getOrNull());
    }

    @Test
    @DisplayName("set(0L) ou set(-1L) -> IllegalArgumentException (sentinel ROOT reserve, exprime via clear())")
    void shouldRejectZeroOrNegativeHotelId() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> TenantContext.set(0L));
        assertTrue(zero.getMessage().contains("positive"),
                "Le message doit expliquer que la valeur doit etre strictement positive");
        assertTrue(zero.getMessage().contains("ROOT"),
                "Le message doit nommer le sentinel ROOT pour orienter le developpeur");

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> TenantContext.set(-1L));
        assertTrue(negative.getMessage().contains("positive"));

        // Le contexte doit rester vierge dans les deux cas.
        assertFalse(TenantContext.isSet());
        assertNull(TenantContext.getOrNull());
    }

    @Test
    @DisplayName("ThreadLocal isole bien : un thread B ne voit pas le tenant pose par A")
    void shouldIsolateBetweenThreads() throws InterruptedException {
        TenantContext.set(100L);

        AtomicReference<Long> seenByOtherThread = new AtomicReference<>();
        AtomicReference<Boolean> isSetByOtherThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            try {
                seenByOtherThread.set(TenantContext.getOrNull());
                isSetByOtherThread.set(TenantContext.isSet());
            } finally {
                done.countDown();
            }
        }, "tenant-isolation-test");

        other.start();
        assertTrue(done.await(2, TimeUnit.SECONDS), "Le thread secondaire n'a pas termine a temps");

        assertNull(seenByOtherThread.get(), "Le tenant doit etre invisible depuis un autre thread");
        assertFalse(isSetByOtherThread.get(), "isSet doit retourner false dans un autre thread");

        // Le thread principal voit toujours sa valeur.
        assertEquals(100L, TenantContext.get());
    }

    @Test
    @DisplayName("Apres set+clear, getOrNull retourne null sans exception (pas de fuite)")
    void shouldNotLeakAfterClear() {
        TenantContext.set(55L);
        TenantContext.clear();

        // Re-acces : aucune valeur residuelle, et getOrNull est silencieux.
        assertNull(TenantContext.getOrNull());
        assertFalse(TenantContext.isSet());

        // Et on doit pouvoir re-positionner sans souci.
        TenantContext.set(99L);
        assertEquals(99L, TenantContext.get());
    }
}
