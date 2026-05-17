package com.cityprojects.citybackend.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.finance.AffectationPaiement}.
 *
 * <p>Tour 45 : ajout de {@code ligneFactureId} (nullable) pour exposer la
 * granularite "paiement de lignes selectionnees".</p>
 */
public record AffectationPaiementDto(
        Long affectationId,
        Long paiementId,
        Long factureId,
        Long ligneFactureId,
        BigDecimal montantAffecte,
        Instant dateAffectation,
        /** Numéro de facture (résolu côté service). */
        String numeroFacture) {

    public AffectationPaiementDto withResolvedNames(String numFact) {
        return new AffectationPaiementDto(
                affectationId, paiementId, factureId, ligneFactureId,
                montantAffecte, dateAffectation, numFact);
    }
}
