package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler du night audit (Tour 13).
 *
 * <p>Le night audit ne s'execute PAS automatiquement. Le scheduler se contente
 * d'envoyer 2 notifications SSE :</p>
 * <ul>
 *   <li><b>11:57 (3 min avant)</b> : alerte broadcast a tous les utilisateurs
 *       connectes (cf. {@code regles_night_audit.txt} §5).</li>
 *   <li><b>12:00 pile</b> : notification ciblee aux roles SUPERADMIN/ADMIN/NIGHTAUDIT
 *       pour ouvrir le modal de lancement (cf. {@code regles_night_audit.txt} §86).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>{@link Hotel} est une entite globale (non scopee par {@code @TenantId}) :
 * {@code findByActifTrueOrderByHotelNom()} retourne TOUS les hotels actifs
 * (pas de filtre tenant a ce niveau, on est en mode batch / cross-tenant).
 * Pour CHAQUE hotel, on positionne le {@link TenantContext} et le {@link MDC}
 * autour de l'envoi de notification, et on les nettoie en {@code finally}.</p>
 *
 * <h3>Coexistence avec @EnableAsync</h3>
 * <p>Spring autorise les deux activations sur la meme application context.
 * Les pools sont distincts (taskScheduler vs asyncExecutor). Activation conditionnelle
 * via {@code city.scheduler.enabled} (defaut {@code true}, desactive en tests).</p>
 */
@Component
@ConditionalOnProperty(name = "city.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NightAuditScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NightAuditScheduler.class);

    private static final String ALERT_3MIN_MESSAGE =
            "Attention, le Night Audit va commencer dans 3 minutes. "
            + "Veuillez terminer vos operations en cours.";

    private final HotelRepository hotelRepository;
    private final NightAuditNotificationService notificationService;

    public NightAuditScheduler(HotelRepository hotelRepository,
                               NightAuditNotificationService notificationService) {
        this.hotelRepository = hotelRepository;
        this.notificationService = notificationService;
    }

    /**
     * Alerte 3 minutes avant l'heure du night audit.
     *
     * <p>Cron : tous les jours a 11:57:00 Africa/Nouakchott.</p>
     */
    @Scheduled(cron = "0 57 11 * * *", zone = "Africa/Nouakchott")
    public void alertThreeMinutesBefore() {
        logger.info("Scheduler night audit : alerte 3 minutes (11:57 Africa/Nouakchott)");
        forEachActiveHotel(hotel ->
                notificationService.broadcastAlertToAllUsers(hotel.getHotelId(), ALERT_3MIN_MESSAGE));
    }

    /**
     * Notification a midi pour ouvrir le modal de lancement chez les
     * ADMIN/SUPERADMIN/NIGHTAUDIT.
     *
     * <p>Cron : tous les jours a 12:00:00 Africa/Nouakchott.</p>
     */
    @Scheduled(cron = "0 0 12 * * *", zone = "Africa/Nouakchott")
    public void notifyAdminAtNoon() {
        logger.info("Scheduler night audit : notification admins (12:00 Africa/Nouakchott)");
        forEachActiveHotel(hotel ->
                notificationService.notifyAdminsForLaunch(hotel.getHotelId()));
    }

    /**
     * Pour chaque hotel actif, positionne le {@link TenantContext} + {@link MDC}
     * et execute l'action. Les ressources thread-locales sont systematiquement
     * nettoyees en {@code finally} pour eviter la fuite sur le pool de
     * {@code TaskScheduler}.
     */
    private void forEachActiveHotel(java.util.function.Consumer<Hotel> action) {
        List<Hotel> hotels = hotelRepository.findByActifTrueOrderByHotelNom();
        for (Hotel hotel : hotels) {
            try {
                TenantContext.set(hotel.getHotelId());
                MDC.put("hotel_id", String.valueOf(hotel.getHotelId()));
                action.accept(hotel);
            } catch (RuntimeException e) {
                // On loggue mais on continue : l'echec sur 1 hotel ne doit pas
                // empecher les autres de recevoir leur notification.
                logger.error("Erreur scheduler night audit pour hotelId={}", hotel.getHotelId(), e);
            } finally {
                TenantContext.clear();
                MDC.remove("hotel_id");
            }
        }
    }
}
