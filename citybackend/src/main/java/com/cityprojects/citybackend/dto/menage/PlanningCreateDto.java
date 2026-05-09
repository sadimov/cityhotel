package com.cityprojects.citybackend.dto.menage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de creation/modification d'un creneau de planning.
 */
public record PlanningCreateDto(
        @NotNull(message = "error.planning.personnelId.required")
        Long personnelId,

        @NotNull(message = "error.planning.dateTravail.required")
        LocalDate dateTravail,

        @NotNull(message = "error.planning.heureDebut.required")
        LocalTime heureDebut,

        @NotNull(message = "error.planning.heureFin.required")
        LocalTime heureFin,

        Boolean disponible,

        @Size(max = 1000, message = "error.planning.commentaires.tooLong")
        String commentaires) {
}
