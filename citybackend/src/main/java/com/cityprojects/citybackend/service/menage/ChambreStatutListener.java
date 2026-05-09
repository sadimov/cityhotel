package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.event.TacheCommenceeEvent;
import com.cityprojects.citybackend.common.event.TacheTermineeEvent;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.service.hebergement.ChambreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener evenementiel cross-modules - Tour 30 (Workflows B et C).
 *
 * <h3>Workflow B - {@link #onTacheTerminee(TacheTermineeEvent)}</h3>
 * <ul>
 *   <li>QUOTIDIEN ou GRAND_MENAGE TERMINEE -&gt; chambre NETTOYAGE -&gt; DISPONIBLE.</li>
 *   <li>MAINTENANCE TERMINEE -&gt; chambre MAINTENANCE -&gt; DISPONIBLE.</li>
 * </ul>
 *
 * <h3>Workflow C - {@link #onTacheCommencee(TacheCommenceeEvent)}</h3>
 * <ul>
 *   <li>MAINTENANCE PLANIFIEE -&gt; commencer() -&gt; chambre DISPONIBLE -&gt;
 *       MAINTENANCE (blocage immediat).</li>
 *   <li>QUOTIDIEN / GRAND_MENAGE : no-op au demarrage. La chambre est deja
 *       NETTOYAGE depuis le checkOut(). Pas de mutation supplementaire.</li>
 * </ul>
 *
 * <h3>Resilience</h3>
 * <p>{@code tryChangerStatut} log WARN sans rethrow si la transition est
 * refusee ({@link BusinessException}) ou si la chambre n'existe plus
 * ({@link ResourceNotFoundException}). La TX du listener (REQUIRES_NEW) est
 * commit independante de la TX d'origine - un echec ici n'invalide pas la
 * pose de la fin/debut de tache.</p>
 *
 * <h3>Pourquoi ici (package menage)</h3>
 * <p>C'est le module ménage qui pilote les transitions de chambre lors de la
 * vie d'une tache. Le module hebergement reste responsable des transitions
 * "metier" (check-in/check-out) - le ménage applique les transitions
 * "operationnelles" (post-nettoyage, maintenance).</p>
 */
@Component
public class ChambreStatutListener {

    private static final Logger logger = LoggerFactory.getLogger(ChambreStatutListener.class);

    private final ChambreService chambreService;

    public ChambreStatutListener(ChambreService chambreService) {
        this.chambreService = chambreService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTacheTerminee(TacheTermineeEvent event) {
        logger.info("Event recu : TacheTerminee tacheId={} chambreId={} type={} hotelId={}",
                event.tacheId(), event.chambreId(), event.typeNettoyage(), event.hotelId());

        // Snapshot du TenantContext / MDC : le listener tourne sur le MEME
        // thread que l'appelant (Spring AFTER_COMMIT synchrone) - sans cette
        // sauvegarde, le clear() en finally effacerait l'etat du thread appelant.
        Long previousTenant = TenantContext.getOrNull();
        String previousMdcHotel = MDC.get("hotel_id");

        TenantContext.clear();
        TenantContext.set(event.hotelId());
        MDC.put("hotel_id", String.valueOf(event.hotelId()));
        try {
            TypeNettoyage type = event.typeNettoyage();
            if (type == TypeNettoyage.QUOTIDIEN || type == TypeNettoyage.GRAND_MENAGE) {
                tryChangerStatut(event.chambreId(), StatutChambre.DISPONIBLE,
                        "tache " + type.name() + " terminee");
            } else if (type == TypeNettoyage.MAINTENANCE) {
                tryChangerStatut(event.chambreId(), StatutChambre.DISPONIBLE,
                        "tache MAINTENANCE terminee");
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTacheCommencee(TacheCommenceeEvent event) {
        logger.info("Event recu : TacheCommencee tacheId={} chambreId={} type={} hotelId={}",
                event.tacheId(), event.chambreId(), event.typeNettoyage(), event.hotelId());

        if (event.typeNettoyage() != TypeNettoyage.MAINTENANCE) {
            // QUOTIDIEN / GRAND_MENAGE : la chambre est deja NETTOYAGE (post
            // check-out). Pas de mutation au demarrage de la tache.
            logger.debug("TacheCommencee {} - no-op au demarrage", event.typeNettoyage());
            return;
        }

        // Snapshot identique a onTacheTerminee.
        Long previousTenant = TenantContext.getOrNull();
        String previousMdcHotel = MDC.get("hotel_id");

        TenantContext.clear();
        TenantContext.set(event.hotelId());
        MDC.put("hotel_id", String.valueOf(event.hotelId()));
        try {
            tryChangerStatut(event.chambreId(), StatutChambre.MAINTENANCE,
                    "tache MAINTENANCE commencee");
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

    /**
     * Tente la transition de statut chambre. Capture {@link BusinessException}
     * (transition metier refusee) et {@link ResourceNotFoundException}
     * (chambre supprimee entre-temps) en WARN sans rethrow.
     *
     * <p>Toute autre {@link RuntimeException} reste relayee : c'est un bug
     * inattendu (ex. NPE), il doit remonter et faire echouer la TX du listener
     * pour etre detecte.</p>
     */
    private void tryChangerStatut(Long chambreId, StatutChambre cible, String raison) {
        try {
            chambreService.changerStatut(chambreId, cible);
            logger.info("Chambre {} -> {} ({})", chambreId, cible, raison);
        } catch (BusinessException ex) {
            logger.warn("Transition chambre {} -> {} refusee ({}) : {}",
                    chambreId, cible, raison, ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            logger.warn("Chambre {} introuvable ({}) : {}", chambreId, raison, ex.getMessage());
        }
    }
}
