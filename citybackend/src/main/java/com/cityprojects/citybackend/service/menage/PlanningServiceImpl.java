package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.menage.PlanningCreateDto;
import com.cityprojects.citybackend.dto.menage.PlanningDto;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.Planning;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.menage.PlanningMapper;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.PlanningRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementation de {@link PlanningService}.
 *
 * <p>Validation : pas de chevauchement de creneaux pour un meme personnel
 * sur une meme date (verifie cote applicatif via
 * {@link PlanningRepository#countConflits}).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class PlanningServiceImpl implements PlanningService {

    private static final Logger logger = LoggerFactory.getLogger(PlanningServiceImpl.class);

    private final PlanningRepository planningRepository;
    private final PersonnelRepository personnelRepository;
    private final PlanningMapper mapper;

    public PlanningServiceImpl(PlanningRepository planningRepository,
                               PersonnelRepository personnelRepository,
                               PlanningMapper mapper) {
        this.planningRepository = planningRepository;
        this.personnelRepository = personnelRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PlanningDto create(PlanningCreateDto dto) {
        logger.info("Creation planning personnel={} date={}", dto.personnelId(), dto.dateTravail());
        validerCoherence(dto);

        // Sentinel exclu : -1L pour autoriser tous les chevauchements potentiels
        long conflits = planningRepository.countConflits(dto.personnelId(), dto.dateTravail(),
                dto.heureDebut(), dto.heureFin(), -1L);
        if (conflits > 0) {
            throw new BusinessException("error.planning.conflit");
        }

        Planning entity = mapper.toEntity(dto);
        if (entity.getDisponible() == null) {
            entity.setDisponible(Boolean.TRUE);
        }
        // PAS de setHotelId : Hibernate via @TenantId.
        return enrichDto(planningRepository.save(entity));
    }

    @Override
    @Transactional
    public PlanningDto update(Long planningId, PlanningCreateDto dto) {
        logger.info("Modification planning id={}", planningId);
        Planning entity = planningRepository.findById(planningId)
                .orElseThrow(() -> new ResourceNotFoundException("error.planning.notFound"));

        validerCoherence(dto);
        long conflits = planningRepository.countConflits(dto.personnelId(), dto.dateTravail(),
                dto.heureDebut(), dto.heureFin(), planningId);
        if (conflits > 0) {
            throw new BusinessException("error.planning.conflit");
        }

        entity.setPersonnelId(dto.personnelId());
        entity.setDateTravail(dto.dateTravail());
        entity.setHeureDebut(dto.heureDebut());
        entity.setHeureFin(dto.heureFin());
        entity.setDisponible(dto.disponible() != null ? dto.disponible() : Boolean.TRUE);
        entity.setCommentaires(dto.commentaires());
        return enrichDto(planningRepository.save(entity));
    }

    @Override
    public PlanningDto findById(Long planningId) {
        Planning entity = planningRepository.findById(planningId)
                .orElseThrow(() -> new ResourceNotFoundException("error.planning.notFound"));
        return enrichDto(entity);
    }

    @Override
    public Page<PlanningDto> page(Pageable pageable) {
        Page<Planning> page = planningRepository.findAll(pageable);
        java.util.Map<Long, String> noms = batchNomsPersonnels(page.getContent());
        return page.map(p -> mapper.toDto(p).withResolvedNames(noms.get(p.getPersonnelId())));
    }

    /** Batch lookup personnel → nom (anti-N+1). Extrait pour réutilisation. */
    private java.util.Map<Long, String> batchNomsPersonnels(java.util.Collection<Planning> entities) {
        java.util.Set<Long> ids = entities.stream()
                .map(Planning::getPersonnelId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) return java.util.Map.of();
        java.util.Map<Long, String> result = new java.util.HashMap<>();
        personnelRepository.findAllById(ids).forEach(p ->
                result.put(p.getPersonnelId(), p.getNomComplet()));
        return result;
    }

    @Override
    public List<PlanningDto> findByPersonnel(Long personnelId, LocalDate date) {
        // Hibernate filtre via @TenantId
        Personnel p = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        String nomPersonnel = p.getNomComplet();
        return planningRepository.findByPersonnelIdAndDateTravailOrderByHeureDebutAsc(personnelId, date)
                .stream()
                .map(pl -> mapper.toDto(pl).withResolvedNames(nomPersonnel))
                .toList();
    }

    @Override
    public List<PlanningDto> findByDate(LocalDate date) {
        return enrichList(planningRepository.findByDateTravailOrderByPersonnelIdAscHeureDebutAsc(date));
    }

    @Override
    public List<PlanningDto> findDisponibles(LocalDate date) {
        return enrichList(planningRepository.findByDateTravailAndDisponibleTrueOrderByPersonnelIdAscHeureDebutAsc(date));
    }

    private PlanningDto enrichDto(Planning entity) {
        String nom = (entity.getPersonnelId() != null)
                ? personnelRepository.findById(entity.getPersonnelId())
                        .map(Personnel::getNomComplet).orElse(null)
                : null;
        return mapper.toDto(entity).withResolvedNames(nom);
    }

    private List<PlanningDto> enrichList(List<Planning> entities) {
        if (entities.isEmpty()) return List.of();
        java.util.Set<Long> ids = entities.stream()
                .map(Planning::getPersonnelId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> noms = ids.isEmpty() ? java.util.Map.of()
                : personnelRepository.findAllById(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Personnel::getPersonnelId, Personnel::getNomComplet));
        return entities.stream()
                .map(p -> mapper.toDto(p).withResolvedNames(noms.get(p.getPersonnelId())))
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long planningId) {
        logger.info("Suppression planning id={}", planningId);
        Planning entity = planningRepository.findById(planningId)
                .orElseThrow(() -> new ResourceNotFoundException("error.planning.notFound"));
        planningRepository.delete(entity);
    }

    private void validerCoherence(PlanningCreateDto dto) {
        // Personnel doit exister et appartenir au tenant
        Personnel p = personnelRepository.findById(dto.personnelId())
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        if (Boolean.FALSE.equals(p.getActif())) {
            throw new BusinessException("error.personnel.notActive");
        }
        if (!dto.heureFin().isAfter(dto.heureDebut())) {
            throw new BusinessException("error.planning.heures.invalid");
        }
    }
}
