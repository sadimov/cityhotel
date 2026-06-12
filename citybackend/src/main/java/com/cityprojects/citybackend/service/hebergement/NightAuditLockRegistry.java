package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.repository.hebergement.JourneeHoteliereRepository;
import com.cityprojects.citybackend.repository.hebergement.JourneeHoteliereRepository.ActiveDayProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mémoire des hôtels actuellement en {@code CLOTURE_EN_COURS}.
 *
 * <p>Consulté à chaque requête HTTP modifiante par {@code NightAuditLockFilter}
 * — un set en mémoire évite un SELECT par requête. Le registry est :
 * <ul>
 *   <li><b>Initialisé</b> au démarrage de l'application via
 *       {@link #rebuildFromDatabase()} (cas crash + restart : si une journée
 *       était en CLOTURE_EN_COURS au moment du kill, elle l'est encore en BD).</li>
 *   <li><b>Mis à jour</b> par {@code HotelDayService.startClosure()} (add) et
 *       {@code HotelDayService.completeClosure()} / {@code abortClosure()} (remove).</li>
 * </ul>
 *
 * <p>Thread-safety : {@link ConcurrentHashMap#newKeySet()} pour le Set, lectures
 * et écritures concurrentes sûres.</p>
 *
 * <p><b>Mode dégradé</b> : si on perd la cohérence registry/BD (jamais arrivé
 * en pratique mais théoriquement possible si {@code add} échoue), une requête
 * HTTP peut passer alors qu'elle aurait dû être bloquée. La garde forte reste
 * la transaction DB du {@code NightAuditService} qui verrouille pessimistically
 * la journée — toute écriture concurrente sera serialisée derrière elle.</p>
 */
@Component
public class NightAuditLockRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditLockRegistry.class);

    private final Set<Long> locked = ConcurrentHashMap.newKeySet();
    private final JourneeHoteliereRepository repository;

    public NightAuditLockRegistry(JourneeHoteliereRepository repository) {
        this.repository = repository;
    }

    /** Marque {@code hotelId} comme verrouillé (CLOTURE_EN_COURS). */
    public void lock(Long hotelId) {
        locked.add(hotelId);
        logger.info("Night audit lock acquis : hotelId={}", hotelId);
    }

    /** Libère {@code hotelId} (transitions vers CLOTUREE ou OUVERTE). */
    public void unlock(Long hotelId) {
        if (locked.remove(hotelId)) {
            logger.info("Night audit lock libéré : hotelId={}", hotelId);
        }
    }

    /** {@code true} si une requête HTTP write doit être rejetée pour cet hôtel. */
    public boolean isLocked(Long hotelId) {
        return hotelId != null && locked.contains(hotelId);
    }

    /**
     * Reconstruit le registry depuis la BD au démarrage. Indispensable :
     * si l'app crash pendant un night audit, le flag CLOTURE_EN_COURS reste
     * en BD ; sans rebuild, après restart, les writes seraient autorisés à
     * tort pendant que la journée est toujours en clôture.
     *
     * <p>Cross-tenant : on lit TOUS les hôtels via {@code TenantScope.runAsRoot}
     * (Hibernate bypass le filtre @TenantId). La requête native cible
     * explicitement {@code hotel_id} (cf. repository).</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildFromDatabase() {
        TenantScope.runAsRoot(() -> {
            for (ActiveDayProjection proj : repository.findAllActiveCrossTenant()) {
                if ("CLOTURE_EN_COURS".equals(proj.getEtat())) {
                    locked.add(proj.getHotelId());
                }
            }
            return null;
        });
        if (!locked.isEmpty()) {
            logger.warn("Night audit lock registry rebuild : {} hôtel(s) en CLOTURE_EN_COURS au démarrage",
                    locked.size());
        } else {
            logger.info("Night audit lock registry rebuild : aucun hôtel verrouillé");
        }
    }

    /** Vue immuable (test/diagnostic uniquement). */
    public Set<Long> snapshot() {
        return Set.copyOf(locked);
    }
}
