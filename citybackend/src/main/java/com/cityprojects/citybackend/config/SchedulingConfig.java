package com.cityprojects.citybackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la planification Spring ({@link org.springframework.scheduling.annotation.Scheduled})
 * pour les jobs batch (Tour 13 : night audit).
 *
 * <p><b>Conditionnel</b> : la configuration n'est active que si
 * {@code city.scheduler.enabled=true} (defaut). Les tests positionnent
 * {@code city.scheduler.enabled=false} dans {@code application-test.properties}
 * pour eviter le declenchement intempestif des crons (11:57 / 12:00) pendant
 * les tests, ce qui pourrait perturber le {@code TenantContext} thread-local
 * partage avec les threads de test.</p>
 *
 * <p>Coexistence avec {@code @EnableAsync} (declare sur {@code CitybackendApplication})
 * : Spring autorise les deux activations sur la meme application context, les
 * pools sont distincts (taskScheduler vs asyncExecutor par defaut).</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "city.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
