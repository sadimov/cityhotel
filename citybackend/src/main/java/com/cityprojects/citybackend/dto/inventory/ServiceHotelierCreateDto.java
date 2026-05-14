package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO d'entree pour la creation/modification d'un service hotelier.
 */
public record ServiceHotelierCreateDto(
        @NotNull(message = "error.serviceHotelier.typeServiceId.required")
        Long typeServiceId,

        @NotBlank(message = "error.serviceHotelier.code.blank")
        @Size(max = 20, message = "error.serviceHotelier.code.tooLong")
        String code,

        @NotBlank(message = "error.serviceHotelier.nom.blank")
        @Size(max = 255, message = "error.serviceHotelier.nom.tooLong")
        String nom,

        String description,

        @NotNull(message = "error.serviceHotelier.prixUnitaire.required")
        @DecimalMin(value = "0.0", inclusive = true,
                message = "error.serviceHotelier.prixUnitaire.negative")
        BigDecimal prixUnitaire,

        @NotBlank(message = "error.serviceHotelier.unite.blank")
        @Size(max = 20, message = "error.serviceHotelier.unite.tooLong")
        String unite) {
}
