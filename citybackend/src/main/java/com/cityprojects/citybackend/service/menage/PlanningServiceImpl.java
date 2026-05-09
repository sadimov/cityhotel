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
        return mapper.toDto(planningRepository.save(entity));
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
        return mapper.toDto(planningRepository.save(entity));
    }

    @Override
    public PlanningDto findById(Long planningId) {
        return planningRepository.findById(planningId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("error.planning.notFound"));
    }

    @Override
    public List<PlanningDto> findByPersonnel(Long personnelId, LocalDate date) {
        // Hibernate filtre via @TenantId
        personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        return planningRepository.findByPersonnelIdAndDateTravailOrderByHeureDebutAsc(personnelId, date)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<PlanningDto> findByDate(LocalDate date) {
        return planningRepository.findByDateTravailOrderByPersonnelIdAscHeureDebutAsc(date)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<PlanningDto> findDisponibles(LocalDate date) {
        return planningRepository.findByDateTravailAndDisponibleTrueOrderByPersonnelIdAscHeureDebutAsc(date)
                .stream().map(mapper::toDto).toList();
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
