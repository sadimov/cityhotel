package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler de generation du planning ménage du jour - Tour 30 (Workflow A).
 *
 * <p>Cron : 12:05 Africa/Nouakchott, soit 5 minutes apres le declenchement
 * potentiel du night audit (12:00). Si l'event Spring publie depuis
 * {@code ReservationServiceImpl#checkOut()} a manque (panne, redemarrage
 * entre publish et listener, etc.), ce scheduler rattrape les check-out de
 * la journee.</p>
 *
 * <h3>Pattern multi-tenant (reproduit de {@code NightAuditScheduler})</h3>
 * <p>{@code Hotel} est une entite globale (non scopee {@code @TenantId}) :
 * {@code findByActifTrueOrderByHotelNom()} retourne TOUS les hotels actifs.
 * Pour chaque hotel : on positionne {@link TenantContext} + {@link MDC}, on
 * appelle {@link MenagePlanningService#genererPlanningDuJour}, puis on
 * nettoie en {@code finally}. Une exception sur 1 hotel n'empeche pas les
 * autres.</p>
 *
 * <h3>Activation conditionnelle</h3>
 * <p>{@code city.scheduler.enabled} (defaut {@code true}, desactive en
 * tests via {@code application-test.properties}).</p>
 */
@Component
@ConditionalOnProperty(name = "city.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MenagePlanningScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MenagePlanningScheduler.class);

    private final HotelRepository hotelRepository;
    private final MenagePlanningService menagePlanningService;
    private final Clock clock;

    public MenagePlanningScheduler(HotelRepository hotelRepository,
                                   MenagePlanningService menagePlanningService,
                                   Clock clock) {
        this.hotelRepository = hotelRepository;
        this.menagePlanningService = menagePlanningService;
        this.clock = clock;
    }

    /**
     * Genere le planning ménage du jour pour chaque hotel actif.
     * Cron : tous les jours a 12:05:00 Africa/Nouakchott (5 minutes apres
     * le night audit a midi - le rattrapage joue apres que les check-out de
     * la matinee soient consolides).
     */
    @Scheduled(cron = "0 5 12 * * *", zone = "Africa/Nouakchott")
    public void runDailyPlanningGeneration() {
        LocalDate today = LocalDate.now(clock);
        logger.info("Scheduler menage : generation planning du {} (12:05 Africa/Nouakchott)", today);

        List<Hotel> hotels = hotelRepository.findByActifTrueOrderByHotelNom();
        for (Hotel hotel : hotels) {
            try {
                TenantContext.set(hotel.getHotelId());
                MDC.put("hotel_id", String.valueOf(hotel.getHotelId()));
                int created = menagePlanningService.genererPlanningDuJour(today);
                logger.info("Planning menage hotel {} ({}) : {} tache(s) creee(s)",
                        hotel.getHotelId(), hotel.getHotelNom(), created);
            } catch (RuntimeException e) {
                // L'echec sur 1 hotel ne doit pas empecher les autres
                // d'avoir leur planning. Logged en ERROR pour alerting.
                logger.error("Erreur scheduler ménage pour hotelId={}", hotel.getHotelId(), e);
            } finally {
                TenantContext.clear();
                MDC.remove("hotel_id");
            }
        }
    }
}
