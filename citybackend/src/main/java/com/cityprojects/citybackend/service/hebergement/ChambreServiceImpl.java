package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.ChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.ChambreMapper;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation de {@link ChambreService}.
 *
 * <h3>Transitions de statut</h3>
 * <p>Validees explicitement par {@link #checkTransition(StatutChambre, StatutChambre)}.
 * Cf. {@link StatutChambre} pour la matrice complete.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ChambreServiceImpl implements ChambreService {

    private static final Logger logger = LoggerFactory.getLogger(ChambreServiceImpl.class);

    private final ChambreRepository chambreRepository;
    private final TypeChambreRepository typeChambreRepository;
    private final ChambreMapper chambreMapper;

    public ChambreServiceImpl(ChambreRepository chambreRepository,
                              TypeChambreRepository typeChambreRepository,
                              ChambreMapper chambreMapper) {
        this.chambreRepository = chambreRepository;
        this.typeChambreRepository = typeChambreRepository;
        this.chambreMapper = chambreMapper;
    }

    @Override
    @Transactional
    public ChambreDto create(ChambreCreateDto dto) {
        logger.info("Creation chambre : numero={}, typeId={}", dto.numeroChambre(), dto.typeId());

        // Le type doit exister dans le meme tenant (Hibernate filtre auto)
        TypeChambre type = typeChambreRepository.findById(dto.typeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        if (!Boolean.TRUE.equals(type.getActif())) {
            throw new BusinessException("error.chambre.type.inactive");
        }

        if (chambreRepository.existsByNumeroChambre(dto.numeroChambre())) {
            throw new BusinessException("error.chambre.numero.alreadyExists");
        }

        Chambre entity = chambreMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        if (entity.getStatut() == null) {
            entity.setStatut(StatutChambre.DISPONIBLE);
        }

        return chambreMapper.toDto(chambreRepository.save(entity));
    }

    @Override
    @Transactional
    public ChambreDto update(Long chambreId, ChambreCreateDto dto) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        // Type modifie ? validation tenant
        if (!entity.getTypeId().equals(dto.typeId())) {
            TypeChambre type = typeChambreRepository.findById(dto.typeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
            if (!Boolean.TRUE.equals(type.getActif())) {
                throw new BusinessException("error.chambre.type.inactive");
            }
        }

        // Numero modifie ? unicite
        if (!entity.getNumeroChambre().equals(dto.numeroChambre())
                && chambreRepository.existsByNumeroChambre(dto.numeroChambre())) {
            throw new BusinessException("error.chambre.numero.alreadyExists");
        }

        entity.setNumeroChambre(dto.numeroChambre());
        entity.setTypeId(dto.typeId());
        entity.setEtage(dto.etage());
        entity.setNbLits(dto.nbLits());
        entity.setNbPersonnesMax(dto.nbPersonnesMax());
        entity.setEquipements(dto.equipements());
        entity.setDescription(dto.description());
        // statut non modifie ici : passer par changerStatut()

        return chambreMapper.toDto(chambreRepository.save(entity));
    }

    @Override
    public ChambreDto findById(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        return chambreMapper.toDto(entity);
    }

    @Override
    public ChambreDto findByNumero(String numeroChambre) {
        Chambre entity = chambreRepository.findByNumeroChambre(numeroChambre)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        return chambreMapper.toDto(entity);
    }

    @Override
    public Page<ChambreDto> findAll(Pageable pageable) {
        // Tri stable (Tour 14 audit, finding I2) + alias front "dateCreation" -> "createdAt"
        // (finding I4 : alias controller-level pour garder le DTO inchange).
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.asc("numeroChambre"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "chambreId");
        return chambreRepository.findAll(stable).map(chambreMapper::toDto);
    }

    @Override
    public List<ChambreDto> findAllActive() {
        return chambreRepository.findByActifTrueOrderByNumeroChambreAsc().stream()
                .map(chambreMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChambreDto> findByType(Long typeId) {
        return chambreRepository.findByTypeIdAndActifTrueOrderByNumeroChambreAsc(typeId).stream()
                .map(chambreMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChambreDto> findByStatut(StatutChambre statut) {
        return chambreRepository.findByStatutAndActifTrueOrderByNumeroChambreAsc(statut).stream()
                .map(chambreMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChambreDto changerStatut(Long chambreId, StatutChambre nouveauStatut) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        checkTransition(entity.getStatut(), nouveauStatut);
        entity.setStatut(nouveauStatut);
        return chambreMapper.toDto(chambreRepository.save(entity));
    }

    @Override
    @Transactional
    public void deactivate(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        if (entity.getStatut() == StatutChambre.OCCUPEE) {
            throw new BusinessException("error.chambre.cannotDeactivateOccupied");
        }
        entity.setActif(Boolean.FALSE);
        entity.setStatut(StatutChambre.HORS_SERVICE);
        chambreRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        if (Boolean.TRUE.equals(entity.getActif())) {
            // Idempotent : pas d'erreur si deja active.
            return;
        }
        entity.setActif(Boolean.TRUE);
        // Sortie obligatoire de HORS_SERVICE -> DISPONIBLE (la matrice
        // checkTransition() refuse le passage HORS_SERVICE -> DISPONIBLE
        // mais on l'autorise ici dans le cadre de la reactivation explicite,
        // qui est elle-meme un acte d'administration).
        entity.setStatut(StatutChambre.DISPONIBLE);
        chambreRepository.save(entity);
    }

    @Override
    public List<ChambreDto> findDisponibles(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("error.disponibilite.dates.required");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new BusinessException("error.disponibilite.dates.invalid");
        }
        return chambreRepository.findDisponibles(dateDebut, dateFin).stream()
                .map(chambreMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Valide les transitions de statut. Voir {@link StatutChambre} pour la matrice.
     */
    private void checkTransition(StatutChambre actuel, StatutChambre nouveau) {
        if (actuel == nouveau) {
            return;
        }
        switch (nouveau) {
            case OCCUPEE:
                if (actuel != StatutChambre.DISPONIBLE) {
                    throw new BusinessException("error.chambre.transition.toOccupied");
                }
                break;
            case DISPONIBLE:
                if (actuel == StatutChambre.HORS_SERVICE) {
                    throw new BusinessException("error.chambre.transition.fromOutOfService");
                }
                break;
            case MAINTENANCE:
                if (actuel == StatutChambre.OCCUPEE) {
                    throw new BusinessException("error.chambre.transition.maintenanceFromOccupied");
                }
                break;
            case NETTOYAGE:
                // libre depuis OCCUPEE (post check-out) ou DISPONIBLE (cleaning preventif)
                if (actuel == StatutChambre.HORS_SERVICE || actuel == StatutChambre.MAINTENANCE) {
                    throw new BusinessException("error.chambre.transition.invalidToCleaning");
                }
                break;
            case HORS_SERVICE:
                // accessible uniquement depuis MAINTENANCE
                if (actuel != StatutChambre.MAINTENANCE) {
                    throw new BusinessException("error.chambre.transition.outOfServiceRequiresMaintenance");
                }
                break;
            default:
                // exhaustivite : si on ajoute un statut, le compilateur ne nous aide pas
                // (enum classique), donc fallback explicite
                throw new BusinessException("error.chambre.transition.unknown");
        }
    }
}
