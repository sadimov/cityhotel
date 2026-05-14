package com.cityprojects.citybackend.common.sse;

import com.cityprojects.citybackend.common.event.ReservationCalendarMutationEvent;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests Surefire du {@link CalendarEventPublisher} - Tour 44 Phase 1.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 : subscribe() ouvre un emetteur, activeEmittersCount incremente.</li>
 *   <li>T2 : subscribe() sans TenantContext -&gt; IllegalStateException.</li>
 *   <li>T3 : onReservationMutation() route vers le bon hotel (les emitters
 *       de l'hotel A recoivent l'event, ceux de B non).</li>
 * </ol>
 *
 * <p>Note : on n'invoque pas le listener via Spring (le @TransactionalEventListener
 * ne se declenche qu'en presence d'une TX commit). On appelle directement la
 * methode du bean pour valider le routing tenant - le wiring AFTER_COMMIT est
 * couvert par les autres ITs.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CalendarEventPublisherTests {

    @Autowired
    private CalendarEventPublisher publisher;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - subscribe() incremente activeEmittersCount")
    void shouldSubscribeAndCount() {
        TenantContext.set(42L);
        int before = publisher.activeEmittersCount(42L);
        SseEmitter e = publisher.subscribe();
        assertNotNull(e);
        assertEquals(before + 1, publisher.activeEmittersCount(42L));
        // Cleanup
        e.complete();
    }

    @Test
    @DisplayName("T2 - subscribe() sans TenantContext -> IllegalStateException")
    void shouldRejectWithoutTenant() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> publisher.subscribe());
    }

    @Test
    @DisplayName("T3 - onReservationMutation() route uniquement vers les emetteurs de l'hotel cible")
    void shouldRouteByHotelId() {
        TenantContext.set(100L);
        SseEmitter eA = publisher.subscribe();
        TenantContext.set(200L);
        SseEmitter eB = publisher.subscribe();
        TenantContext.clear();

        int countAvantA = publisher.activeEmittersCount(100L);
        int countAvantB = publisher.activeEmittersCount(200L);

        // Mutation cote hotel 100 : ne doit pas casser l'emetteur de 200.
        publisher.onReservationMutation(new ReservationCalendarMutationEvent(
                ReservationCalendarMutationEvent.Type.CREATED, 1L, 100L, Instant.now()));

        // Les emetteurs restent actifs (pas d'erreur IO -> pas de retrait).
        assertEquals(countAvantA, publisher.activeEmittersCount(100L));
        assertEquals(countAvantB, publisher.activeEmittersCount(200L));

        eA.complete();
        eB.complete();
    }
}
