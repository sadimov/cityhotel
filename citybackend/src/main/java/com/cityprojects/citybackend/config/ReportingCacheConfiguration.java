package com.cityprojects.citybackend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration cache du module reporting (Tour 41).
 *
 * <p>{@code @EnableCaching} est deja active dans {@link com.cityprojects.citybackend.CitybackendApplication}.
 * Cette classe declare le {@link CacheManager} primaire et reserve les noms des
 * 20 caches reporting (5 P0 Tour 40 + 15 P1/P2 Tour 41). Les services y stockent
 * les agregats {@code (tenant + bornes + parametres)} -&gt; DTO/byte[] pour eviter
 * les re-calculs sur la fenetre TTL.</p>
 *
 * <p>Implementation {@link ConcurrentMapCacheManager} : aucune dependance externe
 * supplementaire (deja apporte par {@code spring-boot-starter-cache}). Pas de TTL
 * automatique - le palier 1 reste simple et la doctrine palier 2 prevoira Caffeine.
 * Les caches restent corrects sur la duree de vie du process ; le redemarrage les
 * vide naturellement (acceptable pour le reporting read-only).</p>
 *
 * <h3>Cles imposees aux services</h3>
 * <p>Toute methode {@code @Cacheable} doit inclure {@code TenantContext.get()} dans
 * la cle (sinon fuite cross-hotel). Exemple :
 * <pre>
 *   {@code @Cacheable(value = "ca-recap", key = "T(...TenantContext).get() + '-' + #periode + ...")}
 * </pre>
 */
@Configuration
@EnableCaching
public class ReportingCacheConfiguration {

    /** Noms des caches reporting (P0 Tour 40 + P1/P2 Tour 41). */
    public static final String[] REPORTING_CACHE_NAMES = new String[]{
            // P0 Tour 40
            "ca-recap",
            "occupation",
            "stock-alerts",
            "night-audit",
            "top-clients",
            // P1/P2 Tour 41 - hebergement
            "alos",
            "no-show-rate",
            "reservation-sources",
            "kpi-reception",
            // P1/P2 Tour 41 - finance
            "encours-clients",
            "tva-recap",
            "top-societes",
            // P1/P2 Tour 41 - inventory
            "mouvements-valorises",
            "bc-pendants",
            "rotation-produits",
            // P1/P2 Tour 41 - restaurant
            "journal-caisse",
            "top-articles",
            "ticket-margin",
            // P1/P2 Tour 41 - menage
            "recap-taches",
            "charge-personnel",
            // P1/P2 Tour 41 - direction
            "dashboard-direction"
    };

    /**
     * CacheManager primaire pour le reporting. {@code ConcurrentMapCacheManager}
     * pre-initialise avec la liste des noms ({@code allowNullValues = true} par
     * defaut). Marque {@code @Primary} pour ecraser un eventuel manager auto-defini.
     */
    @Bean
    @Primary
    public CacheManager reportingCacheManager() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(REPORTING_CACHE_NAMES);
        manager.setAllowNullValues(false);
        return manager;
    }
}
