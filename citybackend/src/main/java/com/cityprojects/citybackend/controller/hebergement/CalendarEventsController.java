package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.common.sse.CalendarEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST API SSE pour le refresh temps reel du calendrier reservations
 * (Tour 44 Phase 1).
 *
 * <p>Pattern Server-Sent Events natif Spring Web (pas de WebSocket / STOMP).
 * Le client utilise {@code new EventSource('/citybackend/api/hebergement/reservations/events')}.
 * Reconnexion automatique cote browser via header {@code Last-Event-ID}.</p>
 *
 * <p><b>Filtrage tenant</b> : l'abonnement extrait le {@code hotelId} du
 * {@code TenantContext} (alimente par {@code JwtAuthenticationFilter}) et
 * route uniquement les events de cet hotel vers cet emitter. Aucun risque
 * de fuite cross-tenant.</p>
 *
 * <p><b>Roles</b> : tous les acteurs susceptibles d'ouvrir le calendrier.</p>
 */
@RestController
@RequestMapping("/api/hebergement/reservations")
public class CalendarEventsController {

    private final CalendarEventPublisher publisher;

    public CalendarEventsController(CalendarEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Ouvre un canal SSE pour recevoir les events de mutation des reservations
     * de l'hotel courant. Tient ouvert 30 minutes (le client reconnecte
     * automatiquement via EventSource).
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public SseEmitter subscribe() {
        return publisher.subscribe();
    }
}
