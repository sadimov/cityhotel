package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;

/**
 * Ligne d'ecriture dans l'edition de journal (B5).
 */
public record LigneJournalDto(
        String compteCode,
        String compteLibelle,
        BigDecimal debit,
        BigDecimal credit) {
}
