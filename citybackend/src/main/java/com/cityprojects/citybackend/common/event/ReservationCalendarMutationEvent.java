package com.cityprojects.citybackend.common.event;

import java.time.Instant;

/**
 * Event applicatif Spring publie apres COMMIT d'une mutation sur une reservation
 * pour notifier le calendrier (Tour 44 Phase 1 - WebSocket refresh temps reel).
 *
 * <p>Consomme par {@code CalendarEventListener} (cf. package
 * {@code com.cityprojects.citybackend.common.websocket}) qui propage
 * l'evenement vers le topic STOMP {@code /topic/hotels/{hotelId}/reservations}.</p>
 *
 * <h3>Multi-tenant</h3>
 * <p>{@code hotelId} est embarque (snapshot {@code TenantContext.get()} au
 * publish) car le {@code @TransactionalEventListener(AFTER_COMMIT)} peut
 * s'executer apres clear du ThreadLocal par le filtre JWT.</p>
 *
 * @param type           type de mutation (CREATED / UPDATED / DELETED)
 * @param reservationId  identifiant de la reservation impactee
 * @param hotelId        identifiant du tenant (snapshot publish)
 * @param timestamp      horodatage du publish (UTC)
 */
public record ReservationCalendarMutationEvent(
        Type type,
        Long reservationId,
        Long hotelId,
        Instant timestamp) {

    public enum Type {
        CREATED, UPDATED, DELETED
    }

    /** Helper de fabrication horodatee a {@link Instant#now()}. */
    public static ReservationCalendarMutationEvent of(Type type, Long reservationId, Long hotelId) {
        return new ReservationCalendarMutationEvent(type, reservationId, hotelId, Instant.now());
    }
}
