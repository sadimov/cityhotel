package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultat de l'edition d'un journal (B5).
 */
public record JournalEditionDto(
        Long journalId,
        String journalCode,
        String journalLibelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        List<EcritureJournalDto> ecritures,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Instant generatedAt) {
}
