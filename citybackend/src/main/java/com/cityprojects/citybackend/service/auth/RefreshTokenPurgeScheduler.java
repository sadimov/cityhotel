package com.cityprojects.citybackend.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de purge nocturne des refresh tokens expires (Tour 38 C6/C7).
 *
 * <p>Cron : 03:00 Africa/Nouakchott. Pas de TenantContext (operation cross-user
 * by design — supprime tous les tokens dont expires_at &lt; now()).</p>
 *
 * <h3>Activation conditionnelle</h3>
 * <p>{@code city.scheduler.enabled} (defaut {@code true}, desactive en tests
 * via {@code application-test.properties}).</p>
 */
@Component
@ConditionalOnProperty(name = "city.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenPurgeScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenPurgeScheduler.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenPurgeScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Purge des refresh tokens dont l'expiration est passee.
     * Tous les jours a 03:00:00 Africa/Nouakchott.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Nouakchott")
    public void runDailyPurge() {
        try {
            int deleted = refreshTokenService.purgeExpired();
            logger.info("Purge refresh tokens (03:00 Africa/Nouakchott) : {} tokens supprimes", deleted);
        } catch (RuntimeException e) {
            // L'echec ne doit pas faire planter le scheduler — log ERROR pour alerting.
            logger.error("Erreur lors de la purge des refresh tokens", e);
        }
    }
}
