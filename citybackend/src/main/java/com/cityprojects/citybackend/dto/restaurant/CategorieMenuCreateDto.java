package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation/modification d'une categorie de menu.
 * Aucun {@code hotelId} : resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}.
 */
public record CategorieMenuCreateDto(
        @NotBlank(message = "error.categorieMenu.nom.blank")
        @Size(max = 100, message = "error.categorieMenu.nom.tooLong")
        String nom,

        String description,

        @Size(max = 200, message = "error.categorieMenu.iconeUrl.tooLong")
        String iconeUrl,

        @PositiveOrZero(message = "error.categorieMenu.ordre.negative")
        Integer ordre) {
}
