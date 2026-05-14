package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;

/**
 * Ligne d'une rubrique de bilan (B5) - 1 compte = 1 ligne.
 */
public record LigneBilanDto(
        String compteCode,
        String compteLibelle,
        BigDecimal montant) {
}
