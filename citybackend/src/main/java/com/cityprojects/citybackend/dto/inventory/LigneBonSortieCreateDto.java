package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO d'entree pour une ligne de bon de sortie.
 */
public record LigneBonSortieCreateDto(
        @NotNull(message = "error.ligneBs.produit.required")
        Long produitId,

        @NotNull(message = "error.ligneBs.quantite.required")
        @Min(value = 1, message = "error.ligneBs.quantite.tooSmall")
        Integer quantiteDemandee,

        String commentaires) {
}
