package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import jakarta.validation.constraints.NotNull;

/**
 * Requete de changement de statut d'un {@link com.cityprojects.citybackend.entity.restaurant.ArticleMenu}.
 *
 * <p>Voir {@link StatutArticle} pour la liste des valeurs admises.</p>
 */
public record ChangeStatutRequest(
        @NotNull(message = "error.articleMenu.statut.required")
        StatutArticle statut) {
}
