package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO d'entree pour la creation d'un article de menu.
 * Aucun {@code hotelId} : resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}. Le statut
 * initial est {@code ACTIF} (force cote service - non parametrable).
 */
public record ArticleMenuCreateDto(
        @NotBlank(message = "error.articleMenu.code.blank")
        @Size(max = 30, message = "error.articleMenu.code.tooLong")
        String codeArticle,

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
