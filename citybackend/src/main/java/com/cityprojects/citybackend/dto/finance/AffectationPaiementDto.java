package com.cityprojects.citybackend.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.finance.AffectationPaiement}.
 */
public record AffectationPaiementDto(
        Long affectationId,
        Long paiementId,
        Long factureId,
        BigDecimal montantAffecte,
        Instant dateAffectation) {
}
