package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.PlanningCreateDto;
import com.cityprojects.citybackend.dto.menage.PlanningDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion des creneaux de planning du personnel de menage.
 */
public interface PlanningService {

    PlanningDto create(PlanningCreateDto dto);

    PlanningDto update(Long planningId, PlanningCreateDto dto);

    PlanningDto findById(Long planningId);

    /** Liste paginee de tous les creneaux (toutes dates) — utilise par la
     *  page liste planning cote front. */
    Page<PlanningDto> page(Pageable pageable);

    List<PlanningDto> findByPersonnel(Long personnelId, LocalDate date);

    List<PlanningDto> findByDate(LocalDate date);

    /** Liste des creneaux marques disponibles pour une date. */
    List<PlanningDto> findDisponibles(LocalDate date);

    void delete(Long planningId);
}
