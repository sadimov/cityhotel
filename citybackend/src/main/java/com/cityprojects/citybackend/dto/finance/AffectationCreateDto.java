package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree pour affecter un paiement a une facture.
 */
public record AffectationCreateDto(
        @NotNull Long factureId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montantAffecte) {
}
