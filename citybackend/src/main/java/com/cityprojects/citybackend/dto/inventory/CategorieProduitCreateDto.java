package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'une categorie de produits.
 */
public record CategorieProduitCreateDto(
        @NotBlank(message = "error.categorieProduit.code.blank")
        @Size(max = 10, message = "error.categorieProduit.code.tooLong")
        String codeCategorie,

        @NotBlank(message = "error.categorieProduit.nom.blank")
        @Size(max = 100, message = "error.categorieProduit.nom.tooLong")
        String nomCategorie,

        String description) {
}
