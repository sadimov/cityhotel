package com.cityprojects.citybackend.dto.menage;

import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de creation d'une tache de menage.
 *
 * <p>Aucun {@code hotelId} : resolu via TenantContext. {@code personnelId}
 * optionnel a la creation (la tache peut etre assignee plus tard via
 * {@code TacheService.assigner()}).</p>
 *
 * <p>Note : le mono utilisait {@code @Future} sur datePlanifiee. Choix retenu :
 * pas de {@code @Future} pour autoriser une tache a J (aujourd'hui).</p>
 */
public record TacheCreateDto(
        @NotNull(message = "error.tache.chambreId.required")
        Long chambreId,

        Long personnelId,

        TypeNettoyage typeNettoyage,

        @Min(value = 1, message = "error.tache.priorite.range")
        @Max(value = 3, message = "error.tache.priorite.range")
        Integer priorite,

        @NotNull(message = "error.tache.datePlanifiee.required")
        LocalDate datePlanifiee,

        LocalTime heureDebutPrevue,

        LocalTime heureFinPrevue,

        @Size(max = 1000, message = "error.tache.commentaires.tooLong")
        String commentaires,

        @Size(max = 500, message = "error.tache.materiel.tooLong")
        String materielNecessaire) {
}
