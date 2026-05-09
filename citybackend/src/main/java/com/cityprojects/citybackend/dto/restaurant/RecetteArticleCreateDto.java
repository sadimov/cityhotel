package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO d'entree pour la creation d'une ligne de recette (Tour 25).
 *
 * <p>Aucun {@code hotelId} : resolu via TenantContext.</p>
 */
public record RecetteArticleCreateDto(
        @NotNull(message = "error.recetteArticle.article.required")
        Long articleId,

        @NotNull(message = "error.recetteArticle.produit.required")
        Long produitId,

        @NotNull(message = "error.recetteArticle.quantite.required")
        @DecimalMin(value = "0.0001", message = "error.recetteArticle.quantite.tooSmall")
        BigDecimal quantiteParUnite,

        @Size(max = 20, message = "error.recetteArticle.unite.tooLong")
        String unite,

        String note) {
}
