package com.cityprojects.citybackend.dto.menage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO d'assignation d'une tache a un membre du personnel.
 */
public record AssignerTacheDto(
        @NotNull(message = "error.tache.personnelId.required")
        Long personnelId,

        @Size(max = 500, message = "error.tache.commentaire.tooLong")
        String commentaire) {
}
