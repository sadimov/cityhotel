package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultat d'un calcul de montant pour un sejour potentiel.
 *
 * <p>Tour 44 Phase 1 : utilise par le calendrier des reservations pour proposer
 * un montant total a la creation. Pas de TVA (palier 1, doctrine prompt POS).
 * {@code montantTtc == montantHt} tant que la TVA n'est pas activee.</p>
 */
public record MontantCalculDto(
        Long typeChambreId,
        Integer totalNuits,
        BigDecimal montantHt,
        BigDecimal montantTtc,
        List<MontantCalculDetailDto> detail) {
}
