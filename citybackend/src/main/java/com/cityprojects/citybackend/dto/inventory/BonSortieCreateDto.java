package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO d'entree pour la creation d'un bon de sortie.
 */
public record BonSortieCreateDto(
        @NotBlank(message = "error.bonSortie.destination.blank")
        @Size(max = 100, message = "error.bonSortie.destination.tooLong")
        String destination,

        String commentaires,

        @NotEmpty(message = "error.bonSortie.lignes.empty")
        @Valid
        List<LigneBonSortieCreateDto> lignes) {
}
