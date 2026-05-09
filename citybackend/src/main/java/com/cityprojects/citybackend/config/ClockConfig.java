package com.cityprojects.citybackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Fournit un {@link Clock} Spring-managed pour tout code metier qui a besoin
 * de l'horodatage courant (calcul d'exercice, expiration, planification).
 * <p>
 * Pourquoi un bean plutot que {@code LocalDate.now()} direct :
 * <ul>
 *   <li><b>Testabilite</b> : un test peut injecter un {@link Clock#fixed} pour
 *       simuler n'importe quelle date sans toucher au temps systeme.</li>
 *   <li><b>Coherence fuseau</b> : on aligne explicitement sur
 *       {@code Africa/Nouakchott} (timezone serveur, cf. CLAUDE.md §11) pour
 *       que la determination de l'exercice comptable ne depende pas du
 *       fuseau de la JVM si demarree ailleurs (CI, conteneur).</li>
 * </ul>
 * Pour un test : declarer un {@code @TestConfiguration} qui expose un
 * {@code @Bean @Primary Clock fixedClock()} ; Spring favorise alors le
 * primaire sur celui-ci.
 */
@Configuration
public class ClockConfig {

    /**
     * Horloge systeme calee sur le fuseau Africa/Nouakchott.
     */
    @Bean
    public Clock systemClock() {
        return Clock.system(ZoneId.of("Africa/Nouakchott"));
    }
}
