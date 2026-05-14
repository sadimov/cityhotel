package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Tour 51bis : DTO d'annulation d'un bon de sortie.
 *
 * <p>Le motif est obligatoire pour tracabilite (audit comptable / inventaire).
 * Un bon de sortie deja LIVRE ne peut pas etre annule (refus cote service -
 * il faut creer un mouvement INVERSE / REGULARISATION).</p>
 */
public record AnnulationBonSortieDto(
        @NotBlank(message = "error.bonSortie.motif.required")
        @Size(max = 500, message = "error.bonSortie.motif.tooLong")
        String motif) {
}
