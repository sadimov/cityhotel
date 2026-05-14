package com.cityprojects.citybackend.dto.inventory;

import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.BonSortie}.
 */
public record BonSortieDto(
        Long bonSortieId,
        String numeroBs,
        String destination,
        StatutBonSortie statut,
        LocalDate dateSortie,
        String commentaires,
        String motifAnnulation,
        Long userId,
        List<LigneBonSortieDto> lignes,
        Instant createdAt,
        Instant updatedAt) {
}
