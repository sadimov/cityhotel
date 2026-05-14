package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.CompteMappingDto;
import com.cityprojects.citybackend.entity.finance.CompteMapping;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.CompteMappingRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation de {@link CompteMappingService}.
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe : toutes les méthodes
 *       exigent un tenant courant. L'initialisation des mappings par défaut
 *       à la création d'un hôtel est déléguée à {@code CompteMappingInitializer}
 *       qui gère lui-même le {@code TenantScope.runAs(hotelId, ...)}.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en
 *       écriture sur {@link #updateMapping}.</li>
 *   <li>Source de vérité du fallback : {@link TypeEvenementComptable#defaultCompteCode()}.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CompteMappingServiceImpl implements CompteMappingService {

    private static final Logger logger = LoggerFactory.getLogger(CompteMappingServiceImpl.class);

    private final CompteMappingRepository mappingRepository;
    private final PlanComptableGeneralRepository pcgRepository;

    public CompteMappingServiceImpl(CompteMappingRepository mappingRepository,
                                    PlanComptableGeneralRepository pcgRepository) {
        this.mappingRepository = mappingRepository;
        this.pcgRepository = pcgRepository;
    }

    @Override
    public String getCompte(TypeEvenementComptable type) {
        if (type == null) {
            throw new BusinessException("error.mapping.typeRequired");
        }
        Optional<CompteMapping> existing = mappingRepository.findByTypeEvenement(type);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActif())) {
            return existing.get().getCompteCode();
        }
        // Fallback : défaut codé. Garantit qu'un hôtel sans mapping personnalisé
        // a toujours un code de compte exploitable.
        return type.defaultCompteCode();
    }

    @Override
    @Transactional
    public CompteMappingDto updateMapping(TypeEvenementComptable type, String compteCode) {
        if (type == null) {
            throw new BusinessException("error.mapping.typeRequired");
        }
        if (compteCode == null || compteCode.isBlank()) {
            throw new BusinessException("error.mapping.compteCodeRequired");
        }
        // Le compte doit exister dans le PCG et être utilisable + actif.
        if (!pcgRepository.existsUtilisableByCode(compteCode)) {
            throw new BusinessException("error.mapping.invalidCompte");
        }

        CompteMapping mapping = mappingRepository.findByTypeEvenement(type)
                .orElseGet(() -> {
                    CompteMapping cm = new CompteMapping();
                    cm.setTypeEvenement(type);
                    cm.setActif(Boolean.TRUE);
                    // PAS de setHotelId : Hibernate via @TenantId.
                    return cm;
                });
        mapping.setCompteCode(compteCode);
        mapping.setActif(Boolean.TRUE);
        CompteMapping saved = mappingRepository.save(mapping);

        logger.info("Mapping comptable mis a jour : type={}, compte={}",
                type, compteCode);

        return toDto(saved, false);
    }

    @Override
    public List<CompteMappingDto> listAll() {
        // Index par typeEvenement des mappings personnalisés en base
        Map<TypeEvenementComptable, CompteMapping> byType = new EnumMap<>(TypeEvenementComptable.class);
        for (CompteMapping cm : mappingRepository.findAllByOrderByTypeEvenementAsc()) {
            byType.put(cm.getTypeEvenement(), cm);
        }

        // Pour chaque valeur de l'enum : renvoyer soit le mapping personnalisé,
        // soit un défaut codé (synthétique, non en base).
        List<CompteMappingDto> result = new ArrayList<>(TypeEvenementComptable.values().length);
        for (TypeEvenementComptable type : TypeEvenementComptable.values()) {
            CompteMapping cm = byType.get(type);
            if (cm != null && Boolean.TRUE.equals(cm.getActif())) {
                result.add(toDto(cm, false));
            } else {
                result.add(toDefaultDto(type));
            }
        }
        return result;
    }

    /** Convertit un mapping persiste en DTO (denormalise le libelle PCG). */
    private CompteMappingDto toDto(CompteMapping entity, boolean defaut) {
        String libelle = pcgRepository.findByCompteCode(entity.getCompteCode())
                .map(PlanComptableGeneral::getLibelle)
                .orElse("");
        return new CompteMappingDto(
                entity.getTypeEvenement(),
                entity.getCompteCode(),
                libelle,
                Boolean.TRUE.equals(entity.getActif()),
                defaut);
    }

    /**
     * Construit un DTO synthetique a partir du defaut code (aucune ligne en
     * base) : utilise par {@link #listAll()} pour exposer les mappings encore
     * non personnalises.
     */
    private CompteMappingDto toDefaultDto(TypeEvenementComptable type) {
        String code = type.defaultCompteCode();
        String libelle = pcgRepository.findByCompteCode(code)
                .map(PlanComptableGeneral::getLibelle)
                .orElse("");
        return new CompteMappingDto(type, code, libelle, true, true);
    }
}
