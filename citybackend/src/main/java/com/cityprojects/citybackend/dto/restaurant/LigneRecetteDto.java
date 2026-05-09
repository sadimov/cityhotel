package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO d'entree pour une ligne de recette dans une saisie groupee
 * {@code setRecetteForArticle} (Tour 25).
 *
 * <p>Pas d'{@code articleId} : c'est le parametre du service qui le porte.</p>
 */
public record LigneRecetteDto(
        @NotNull(message = "error.recetteArticle.produit.required")
        Long produitId,

        @NotNull(message = "error.recetteArticle.quantite.required")
        @DecimalMin(value = "0.0001", message = "error.recetteArticle.quantite.tooSmall")
        BigDecimal quantiteParUnite,

        @Size(max = 20, message = "error.recetteArticle.unite.tooLong")
        String unite,

        String note) {
}
