package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree pour affecter un paiement a une facture (avec granularite ligne
 * optionnelle - Tour 45).
 *
 * <p>{@code ligneFactureId} est nullable : si {@code null}, l'affectation
 * porte sur la facture entiere (legacy pre-Tour 45). Si renseigne, elle porte
 * sur une ligne specifique (paiement de lignes selectionnees).</p>
 */
public record AffectationCreateDto(
        @NotNull Long factureId,
        Long ligneFactureId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montantAffecte) {
}
