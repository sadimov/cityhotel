package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;

/**
 * Ligne d'une rubrique de compte de resultat (B5) - 1 compte = 1 ligne.
 */
public record LigneResultatDto(
        String compteCode,
        String compteLibelle,
        BigDecimal montant) {
}
