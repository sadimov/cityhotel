package com.cityprojects.citybackend.common.sse;

import com.cityprojects.citybackend.common.event.ReservationCalendarMutationEvent;

import java.time.Instant;

/**
 * Payload SSE envoye sur {@code GET /api/hebergement/reservations/events}
 * (Tour 44 Phase 1).
 *
 * <p>Pattern Server-Sent Events natif Spring Web (pas de WebSocket / STOMP
 * pour rester sans dependance additionnelle). EventSource cote navigateur :
 * {@code new EventSource('/api/hebergement/reservations/events?access_token=...')}.
 * Reconnexion automatique cote browser via header {@code Last-Event-ID}.</p>
 *
 * @param type           type de mutation (CREATED / UPDATED / DELETED)
 * @param reservationId  reservation impactee
 * @param hotelId        tenant (filtre cote serveur, le client ne recoit que
 *                       les events de SON hotel)
 * @param timestamp      horodatage UTC du publish
 */
public record CalendarEventDto(
        String type,
        Long reservationId,
        Long hotelId,
        Instant timestamp) {

    public static CalendarEventDto from(ReservationCalendarMutationEvent event) {
        return new CalendarEventDto(
                event.type().name(),
                event.reservationId(),
                event.hotelId(),
                event.timestamp());
    }
}
