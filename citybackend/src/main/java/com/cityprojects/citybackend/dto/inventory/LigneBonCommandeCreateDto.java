package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree pour une ligne de bon de commande (sub-record dans BonCommandeCreateDto).
 */
public record LigneBonCommandeCreateDto(
        @NotNull(message = "error.ligneBc.produit.required")
        Long produitId,

        @NotNull(message = "error.ligneBc.quantite.required")
        @Min(value = 1, message = "error.ligneBc.quantite.tooSmall")
        Integer quantiteCommandee,

        @NotNull(message = "error.ligneBc.prix.required")
        @DecimalMin(value = "0.00", message = "error.ligneBc.prix.negative")
        BigDecimal prixUnitaire) {
}
