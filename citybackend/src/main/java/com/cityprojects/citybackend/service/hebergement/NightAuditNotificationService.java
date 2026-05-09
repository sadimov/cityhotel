package com.cityprojects.citybackend.service.hebergement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diffuseur de notifications Server-Sent Events (SSE) pour le night audit
 * (Tour 13).
 *
 * <p>Chaque utilisateur connecte ouvre un {@link SseEmitter} via
 * {@code GET /api/hebergement/night-audit/notifications}. Le scheduler envoie deux types
 * d'evenements :</p>
 * <ul>
 *   <li><b>{@code night-audit-alert}</b> : a 11:57 (3 min avant le night audit),
 *       diffuse a TOUS les utilisateurs connectes du tenant.</li>
 *   <li><b>{@code night-audit-modal}</b> : a 12:00 pile, envoye uniquement aux
 *       utilisateurs des roles SUPERADMIN, ADMIN, NIGHTAUDIT pour declencher
 *       le modal de lancement.</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>La structure {@code emittersByHotel} indexe les emitters par {@code hotelId}.
 * Les broadcasts iterent uniquement sur les emitters du tenant cible : pas de
 * fuite cross-hotel.</p>
 *
 * <h3>Thread-safety</h3>
 * <p>{@link ConcurrentHashMap} + {@link ConcurrentHashMap#newKeySet()} pour le
 * Set de valeurs. Les iterations concurrentes sur le Set sont sures (vue
 * faiblement coherente, suffisante pour le broadcast).</p>
 */
@Service
public class NightAuditNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditNotificationService.class);

    /** Timeout SSE par defaut : 1h. Le client doit re-souscrire au-dela. */
    private static final long SSE_TIMEOUT_MS = Duration.ofHours(1).toMillis();

    /** Roles autorises a recevoir l'alerte d'ouverture du modal de lancement. */
    private static final Set<String> ROLES_LAUNCH = Set.of("SUPERADMIN", "ADMIN", "NIGHTAUDIT");

    /** Map hotelId -&gt; set d'emitters actifs (1 par utilisateur connecte). */
    private final Map<Long, Set<EmitterEntry>> emittersByHotel = new ConcurrentHashMap<>();

    /**
     * Enregistre un nouvel emitter pour ({@code userId}, {@code hotelId}, {@code roleCode}).
     *
     * <p>Si un emitter existe deja pour ce {@code userId} dans cet hotel, il est
     * ferme avant l'ajout du nouveau (pas de doublon par utilisateur).</p>
     *
     * @return le {@link SseEmitter} ouvert et pret a etre retourne au client
     */
    public SseEmitter register(Long userId, Long hotelId, String roleCode) {
        Set<EmitterEntry> set = emittersByHotel.computeIfAbsent(
                hotelId, h -> ConcurrentHashMap.newKeySet());

        // Ferme et retire toute connexion existante du meme user (cf. brief Tour 13).
        Iterator<EmitterEntry> it = set.iterator();
        while (it.hasNext()) {
            EmitterEntry existing = it.next();
            if (userId.equals(existing.userId)) {
                try {
                    existing.emitter.complete();
                } catch (Exception ignored) {
                    // L'emitter peut deja etre dans un etat ferme - on ignore.
                }
                it.remove();
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        EmitterEntry entry = new EmitterEntry(userId, roleCode, emitter);
        set.add(entry);

        emitter.onCompletion(() -> remove(hotelId, entry));
        emitter.onTimeout(() -> remove(hotelId, entry));
        emitter.onError(t -> remove(hotelId, entry));

        logger.debug("SSE register : userId={}, hotelId={}, role={}", userId, hotelId, roleCode);
        return emitter;
    }

    /**
     * Diffuse une alerte 3-min a TOUS les utilisateurs connectes du tenant.
     */
    public void broadcastAlertToAllUsers(Long hotelId, String message) {
        Set<EmitterEntry> set = emittersByHotel.get(hotelId);
        if (set == null || set.isEmpty()) {
            logger.debug("Broadcast alert : aucun emitter pour hotelId={}", hotelId);
            return;
        }
        for (EmitterEntry entry : set) {
            sendEvent(hotelId, entry, "night-audit-alert", new AlertPayload("ALERT_3MIN", message));
        }
    }

    /**
     * Notifie uniquement les utilisateurs ADMIN/SUPERADMIN/NIGHTAUDIT du tenant
     * pour qu'ils ouvrent le modal de lancement.
     */
    public void notifyAdminsForLaunch(Long hotelId) {
        Set<EmitterEntry> set = emittersByHotel.get(hotelId);
        if (set == null || set.isEmpty()) {
            logger.debug("Notify admins : aucun emitter pour hotelId={}", hotelId);
            return;
        }
        for (EmitterEntry entry : set) {
            if (entry.roleCode != null && ROLES_LAUNCH.contains(entry.roleCode)) {
                sendEvent(hotelId, entry, "night-audit-modal",
                        new AlertPayload("OPEN_LAUNCH_MODAL", "Le night audit peut etre lance."));
            }
        }
    }

    /**
     * Compte le nombre d'emitters actifs pour un hotel (utile pour les tests
     * et le monitoring).
     */
    public int activeEmitterCount(Long hotelId) {
        Set<EmitterEntry> set = emittersByHotel.get(hotelId);
        return set == null ? 0 : set.size();
    }

    private void sendEvent(Long hotelId, EmitterEntry entry, String name, Object data) {
        try {
            entry.emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            // Client deconnecte : retire l'emitter et continue la boucle.
            logger.debug("SSE send echec (client deconnecte ?) : userId={}, hotelId={}",
                    entry.userId, hotelId);
            remove(hotelId, entry);
        } catch (IllegalStateException e) {
            // Emitter deja complete : retire-le proprement.
            logger.debug("SSE deja complete : userId={}, hotelId={}", entry.userId, hotelId);
            remove(hotelId, entry);
        }
    }

    private void remove(Long hotelId, EmitterEntry entry) {
        Set<EmitterEntry> set = emittersByHotel.get(hotelId);
        if (set != null) {
            set.remove(entry);
        }
    }

    /** Entree d'emitter (immuable, identite via {@code emitter}). */
    private static final class EmitterEntry {
        private final Long userId;
        private final String roleCode;
        private final SseEmitter emitter;

        EmitterEntry(Long userId, String roleCode, SseEmitter emitter) {
            this.userId = userId;
            this.roleCode = roleCode;
            this.emitter = emitter;
        }
    }

    /** Payload d'evenement SSE serialise en JSON par Spring. */
    @SuppressWarnings("unused") // accesseurs utilises par Jackson
    private static final class AlertPayload {
        private final String type;
        private final String message;

        AlertPayload(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public String getType() { return type; }
        public String getMessage() { return message; }
    }
}
