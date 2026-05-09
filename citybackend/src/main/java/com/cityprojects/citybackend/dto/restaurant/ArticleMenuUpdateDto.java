package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de modification d'un article de menu.
 *
 * <p>Le {@code codeArticle} N'EST PAS modifiable (cle metier reutilisee dans
 * d'eventuelles integrations - cf. pattern Tour 16 categorie produit).</p>
 *
 * <p>Le {@code statut} se modifie via l'endpoint dedie {@code PATCH .../statut}
 * avec {@link ChangeStatutRequest}.</p>
 */
public record ArticleMenuUpdateDto(
        @NotBlank(message = "error.articleMenu.nom.blank")
        @Size(max = 200, message = "error.articleMenu.nom.tooLong")
        String nom,

        String description,

        @NotNull(message = "error.articleMenu.categorie.required")
        Long categorieId,

        @NotNull(message = "error.articleMenu.prix.required")
        @DecimalMin(value = "0.00", message = "error.articleMenu.prix.negative")
        BigDecimal prix,

        @Size(max = 200, message = "error.articleMenu.imageUrl.tooLong")
        String imageUrl,

        Boolean disponible) {
}
