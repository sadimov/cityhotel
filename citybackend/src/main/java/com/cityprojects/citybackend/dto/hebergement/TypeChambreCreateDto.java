package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de creation d'un type de chambre. Aucun {@code hotelId} : resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}.
 */
public record TypeChambreCreateDto(
        @NotBlank(message = "error.typeChambre.code.blank")
        @Size(max = 10, message = "error.typeChambre.code.tooLong")
        String typeCode,

        @NotBlank(message = "error.typeChambre.nom.blank")
        @Size(max = 100, message = "error.typeChambre.nom.tooLong")
        String typeNom,

        String description,

        @PositiveOrZero(message = "error.typeChambre.superficie.negative")
        BigDecimal superficie,

        @NotNull(message = "error.typeChambre.nbLitsMax.required")
        @Positive(message = "error.typeChambre.nbLitsMax.notPositive")
        Integer nbLitsMax,

        @NotNull(message = "error.typeChambre.nbPersonnesMax.required")
        @Positive(message = "error.typeChambre.nbPersonnesMax.notPositive")
        Integer nbPersonnesMax,

        @PositiveOrZero(message = "error.typeChambre.prixBase.negative")
        BigDecimal prixBase) {
}
