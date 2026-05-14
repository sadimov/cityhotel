package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.StatutExercice;

import java.time.LocalDate;

/**
 * DTO de lecture d'un exercice comptable.
 *
 * <p>Le {@code hotelId} n'est pas expose : reservé au {@code TenantContext}.</p>
 */
public record ExerciceDto(
        Long id,
        String code,
        LocalDate dateDebut,
        LocalDate dateFin,
        StatutExercice statut,
        LocalDate dateCloture,
        String clotureBy
) {}
