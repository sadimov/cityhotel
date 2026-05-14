package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.entity.finance.TauxTvaConfig;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.TauxTvaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Initialise les configurations TVA par défaut pour un hotel donné (B4).
 *
 * <p>Pattern identique à {@code CompteMappingInitializer} et
 * {@code JournalComptableInitializer} : bean séparé, non annoté
 * {@code @RequireTenant}, ouvre lui-même un {@link TenantScope} pour
 * pouvoir tourner depuis un service admin SUPERADMIN (mode ROOT).</p>
 *
 * <p>Idempotent : seuls les types non encore configurés se voient injecter
 * leur défaut codé. Transaction {@link Propagation#REQUIRES_NEW} : isolation
 * complète de la TX de création d'hotel.</p>
 */
@Component
public class TauxTvaConfigInitializer {

    private static final Logger logger = LoggerFactory.getLogger(TauxTvaConfigInitializer.class);

    private final TauxTvaConfigRepository repository;

    public TauxTvaConfigInitializer(TauxTvaConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Crée en base une configuration TVA par défaut pour chaque
     * {@link TypeServiceTva} non encore présent pour l'hotel cible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedDefault(Long hotelId) {
        if (hotelId == null || hotelId <= 0L) {
            throw new BusinessException("error.tva.hotelIdRequired");
        }
        TenantScope.runAs(hotelId, () -> {
            Map<TypeServiceTva, TauxTvaConfig> existing = new EnumMap<>(TypeServiceTva.class);
            for (TauxTvaConfig cfg : repository.findAllByOrderByTypeServiceAsc()) {
                existing.put(cfg.getTypeService(), cfg);
            }
            int created = 0;
            for (TypeServiceTva type : TypeServiceTva.values()) {
                if (existing.containsKey(type)) {
                    continue;
                }
                TauxTvaConfig cfg = new TauxTvaConfig();
                cfg.setTypeService(type);
                cfg.setTaux(type.defaultTaux());
                cfg.setActif(Boolean.TRUE);
                cfg.setLibelle(type.defaultLibelle());
                // PAS de setHotelId : Hibernate via @TenantId resolver.
                repository.save(cfg);
                created++;
            }
            logger.info("Configurations TVA par defaut creees pour hotel {} : {} entrees",
                    hotelId, created);
        });
    }
}
