package com.cityprojects.citybackend.common.sse;

import com.cityprojects.citybackend.common.event.ReservationCalendarMutationEvent;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publisher SSE des evenements calendrier reservations (Tour 44 Phase 1).
 *
 * <h3>Architecture</h3>
 * <p>Un emetteur Server-Sent Events par client connecte. La map est indexee
 * par {@code hotelId} pour broadcast filtre tenant : un client de l'hotel A
 * ne recoit jamais les events de l'hotel B (regle d'or NON NEGOCIABLE,
 * CLAUDE.md racine §6.1).</p>
 *
 * <h3>Reception evenements applicatifs</h3>
 * <p>Le bean ecoute {@link ReservationCalendarMutationEvent} via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} : les events sont
 * propages aux SseEmitter SEULEMENT si la TX d'origine commit (resilience
 * rollback). Une nouvelle TX {@code REQUIRES_NEW} est ouverte pour rester
 * coherent avec le pattern Tour 30 (cf. citybackend/CLAUDE.md §3.6).</p>
 *
 * <h3>Multi-tenant</h3>
 * <p>L'event embarque {@code hotelId} (snapshot publish). Le listener n'a pas
 * besoin de {@code TenantContext} - il route directement vers les emetteurs
 * de cet hotel.</p>
 */
@Component
public class CalendarEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(CalendarEventPublisher.class);

    /**
     * Map hotelId -> liste d'emetteurs actifs.
     * CopyOnWriteArrayList : thread-safe pour les iterations broadcast.
     */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByHotel = new ConcurrentHashMap<>();

    /**
     * Enregistre un nouvel emetteur SSE pour l'hotel courant. Retourne
     * l'emitter au caller (qui le retournera au browser). Lifecycle :
     * <ul>
     *   <li>{@code onCompletion} / {@code onTimeout} / {@code onError} : retire
     *       l'emitter de la map automatiquement ;</li>
     *   <li>{@code timeout} : 30 minutes (le client reconnecte tout seul
     *       via EventSource).</li>
     * </ul>
     */
    public SseEmitter subscribe() {
        Long hotelId = TenantContext.getOrNull();
        if (hotelId == null) {
            throw new IllegalStateException("error.tenant.missing");
        }
        // 30 minutes - le browser reconnecte sur timeout via EventSource.
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        CopyOnWriteArrayList<SseEmitter> list = emittersByHotel.computeIfAbsent(
                hotelId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            logger.debug("SSE emitter retire : hotelId={}, restant={}", hotelId, list.size());
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        // Envoi d'un event "hello" pour ouvrir le canal et confirmer la connexion.
        try {
            emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name("hello")
                    .data(Map.of("hotelId", hotelId, "ts", System.currentTimeMillis())));
        } catch (IOException e) {
            logger.warn("SSE emitter hello echoue : hotelId={}, err={}", hotelId, e.getMessage());
            emitter.completeWithError(e);
        }
        logger.info("SSE emitter abonne : hotelId={}, total={}", hotelId, list.size());
        return emitter;
    }

    /**
     * Listener applicatif : propage les events de mutation reservation vers
     * tous les emitters de l'hotel concerne. AFTER_COMMIT + REQUIRES_NEW pour
     * resilience rollback (cf. citybackend/CLAUDE.md §3.6).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationMutation(ReservationCalendarMutationEvent event) {
        List<SseEmitter> targets = emittersByHotel.get(event.hotelId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        CalendarEventDto payload = CalendarEventDto.from(event);
        // Snapshot pour iteration sans bloquer les ajouts/retraits concurrents.
        for (SseEmitter e : targets) {
            try {
                e.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("reservation." + event.type().name().toLowerCase())
                        .data(payload));
            } catch (IOException io) {
                // Client deconnecte : on retire et on continue.
                logger.debug("SSE send IOException, retrait emitter : hotelId={}, err={}",
                        event.hotelId(), io.getMessage());
                targets.remove(e);
                try {
                    e.completeWithError(io);
                } catch (Exception ignore) {
                    // emitter deja complete - swallow
                }
            }
        }
    }

    /**
     * Visible pour tests : nombre d'emetteurs actifs pour un hotel donne.
     */
    public int activeEmittersCount(Long hotelId) {
        CopyOnWriteArrayList<SseEmitter> l = emittersByHotel.get(hotelId);
        return l == null ? 0 : l.size();
    }
}
