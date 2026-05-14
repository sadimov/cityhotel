package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ecriture dans l'edition de journal (B5).
 */
public record EcritureJournalDto(
        LocalDate dateComptable,
        String numero,
        String libelle,
        String reference,
        List<LigneJournalDto> lignes,
        BigDecimal totalDebitEcriture,
        BigDecimal totalCreditEcriture) {
}
