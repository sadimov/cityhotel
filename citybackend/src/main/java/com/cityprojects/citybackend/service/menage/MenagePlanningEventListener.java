package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.event.ReservationCheckedOutEvent;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener evenementiel - Tour 30 (Workflow A) : sur check-out d'une
 * reservation, generer une tache QUOTIDIEN PLANIFIEE par chambre liberee.
 *
 * <h3>Pattern transactionnel</h3>
 * <ul>
 *   <li>{@code AFTER_COMMIT} : ne s'execute que si la TX d'origine a commit
 *       (resilience rollback - si le checkOut() echoue, aucun planning n'est
 *       genere).</li>
 *   <li>{@code REQUIRES_NEW} : ouvre sa propre TX (la TX d'origine est deja
 *       committee, on ne peut plus s'attacher dessus).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>Le {@code TenantContext} est positionne explicitement depuis l'event
 * (le ThreadLocal du publisher peut avoir ete cleared par le
 * {@code JwtAuthenticationFilter#finally} au moment du callback). Nettoye
 * en {@code finally}.</p>
 *
 * <h3>Resilience</h3>
 * <p>Une exception sur une chambre n'interrompt pas le traitement des autres
 * chambres de la meme reservation : on log WARN et on continue.</p>
 */
@Component
public class MenagePlanningEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MenagePlanningEventListener.class);

    private final MenagePlanningService menagePlanningService;

    public MenagePlanningEventListener(MenagePlanningService menagePlanningService) {
        this.menagePlanningService = menagePlanningService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCheckedOut(ReservationCheckedOutEvent event) {
        logger.info("Event recu : ReservationCheckedOut reservationId={} hotelId={} dateCheckOut={} {} chambre(s)",
                event.reservationId(), event.hotelId(), event.dateCheckOut(),
                event.chambreIds() != null ? event.chambreIds().size() : 0);

        if (event.chambreIds() == null || event.chambreIds().isEmpty()) {
            logger.warn("ReservationCheckedOut sans chambre - skip (reservationId={})", event.reservationId());
            return;
        }

        // IMPORTANT (Tour 30) : le listener tourne sur le MEME thread que la TX
        // d'origine (callback synchrone Spring AFTER_COMMIT). On snapshote le
        // TenantContext et le MDC AVANT mutation pour les restaurer en
        // finally - sinon on efface l'etat du thread appelant (ex: code de
        // test qui appelle reservationService.checkOut() avec un TenantContext
        // deja positionne).
        Long previousTenant = TenantContext.getOrNull();
        String previousMdcHotel = MDC.get("hotel_id");

        TenantContext.clear();
        TenantContext.set(event.hotelId());
        MDC.put("hotel_id", String.valueOf(event.hotelId()));
        try {
            for (Long chambreId : event.chambreIds()) {
                try {
                    menagePlanningService.creerTacheCheckOutSiAbsente(chambreId, event.dateCheckOut());
                } catch (RuntimeException ex) {
                    // Resilience : un echec sur une chambre ne doit pas faire
                    // crasher le traitement des autres chambres de la meme
                    // reservation. Le warn permet le diagnostic ; la TX du
                    // listener continue.
                    logger.warn("Echec generation tache check-out chambre={} date={} : {}",
                            chambreId, event.dateCheckOut(), ex.getMessage());
                }
            }
        } finally {
            TenantContext.clear();
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            }
            if (previousMdcHotel != null) {
                MDC.put("hotel_id", previousMdcHotel);
            } else {
                MDC.remove("hotel_id");
            }
        }
    }
}
