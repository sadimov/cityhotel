package com.cityprojects.citybackend.dto.menage;

import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.menage.Tache}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext).</p>
 */
public record TacheDto(
        Long tacheId,
        Long chambreId,
        Long personnelId,
        StatutTache statut,
        TypeNettoyage typeNettoyage,
        Integer priorite,
        LocalDate datePlanifiee,
        LocalTime heureDebutPrevue,
        LocalTime heureFinPrevue,
        Instant heureDebutReelle,
        Instant heureFinReelle,
        String commentaires,
        String problemesDetectes,
        String materielUtilise,
        Instant createdAt,
        Instant updatedAt) {
}
