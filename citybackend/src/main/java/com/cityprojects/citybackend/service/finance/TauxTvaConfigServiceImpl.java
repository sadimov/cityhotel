package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.TauxTvaConfigDto;
import com.cityprojects.citybackend.entity.finance.TauxTvaConfig;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.TauxTvaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implémentation de {@link TauxTvaConfigService}.
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe. Initialisation des défauts
 *       par hotel déléguée à {@code TauxTvaConfigInitializer} (TenantScope).</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en
 *       écriture sur {@link #update}.</li>
 *   <li>Fallback systématique sur {@link TypeServiceTva#defaultTaux()} pour
 *       éviter de casser le flux métier d'un hotel sans configuration.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TauxTvaConfigServiceImpl implements TauxTvaConfigService {

    private static final Logger logger = LoggerFactory.getLogger(TauxTvaConfigServiceImpl.class);

    private final TauxTvaConfigRepository repository;

    public TauxTvaConfigServiceImpl(TauxTvaConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal getTaux(TypeServiceTva typeService) {
        if (typeService == null) {
            // Cas defensif : on retourne 0 plutot que de planter le flux
            // metier (la TVA est une couche transversale, son indisponibilité
            // ne doit jamais bloquer l'emission d'une facture).
            return BigDecimal.ZERO;
        }
        Optional<TauxTvaConfig> existing = repository.findByTypeService(typeService);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActif())) {
            BigDecimal taux = existing.get().getTaux();
            return (taux != null) ? taux : typeService.defaultTaux();
        }
        return typeService.defaultTaux();
    }

    @Override
    public List<TauxTvaConfigDto> findAll() {
        Map<TypeServiceTva, TauxTvaConfig> byType = new EnumMap<>(TypeServiceTva.class);
        for (TauxTvaConfig cfg : repository.findAllByOrderByTypeServiceAsc()) {
            byType.put(cfg.getTypeService(), cfg);
        }
        List<TauxTvaConfigDto> result = new ArrayList<>(TypeServiceTva.values().length);
        for (TypeServiceTva type : TypeServiceTva.values()) {
            TauxTvaConfig cfg = byType.get(type);
            if (cfg != null) {
                result.add(toDto(cfg, false));
            } else {
                result.add(toDefaultDto(type));
            }
        }
        return result;
    }

    @Override
    public TauxTvaConfigDto findByType(TypeServiceTva typeService) {
        if (typeService == null) {
            throw new BusinessException("error.tva.typeRequired");
        }
        return repository.findByTypeService(typeService)
                .map(cfg -> toDto(cfg, false))
                .orElseGet(() -> toDefaultDto(typeService));
    }

    @Override
    @Transactional
    public TauxTvaConfigDto update(TypeServiceTva typeService, BigDecimal taux,
                                   Boolean actif, String libelle) {
        if (typeService == null) {
            throw new BusinessException("error.tva.typeRequired");
        }
        if (taux == null) {
            throw new BusinessException("error.tva.taux.required");
        }
        if (taux.signum() < 0) {
            throw new BusinessException("error.tva.taux.negatif");
        }
        // Borne haute defensive (99.99) : aligne avec @DecimalMax sur l'entité
        // et le DTO. precision=5, scale=2 -> max possible = 999.99 cote SQL,
        // mais 99.99 suffit pour tout taux fiscal connu.
        if (taux.compareTo(new BigDecimal("99.99")) > 0) {
            throw new BusinessException("error.tva.taux.trop.eleve");
        }

        TauxTvaConfig cfg = repository.findByTypeService(typeService)
                .orElseGet(() -> {
                    TauxTvaConfig fresh = new TauxTvaConfig();
                    fresh.setTypeService(typeService);
                    fresh.setActif(Boolean.TRUE);
                    fresh.setLibelle(typeService.defaultLibelle());
                    // PAS de setHotelId : Hibernate via @TenantId resolver.
                    return fresh;
                });
        cfg.setTaux(taux);
        if (actif != null) {
            cfg.setActif(actif);
        }
        if (libelle != null) {
            cfg.setLibelle(libelle);
        }
        TauxTvaConfig saved = repository.save(cfg);

        logger.info("Config TVA mise a jour : type={}, taux={}, actif={}",
                typeService, taux, saved.getActif());

        return toDto(saved, false);
    }

    private TauxTvaConfigDto toDto(TauxTvaConfig entity, boolean defaut) {
        return new TauxTvaConfigDto(
                entity.getTypeService(),
                entity.getTaux(),
                Boolean.TRUE.equals(entity.getActif()),
                entity.getLibelle(),
                defaut);
    }

    private TauxTvaConfigDto toDefaultDto(TypeServiceTva type) {
        return new TauxTvaConfigDto(
                type,
                type.defaultTaux(),
                true,
                type.defaultLibelle(),
                true);
    }
}
