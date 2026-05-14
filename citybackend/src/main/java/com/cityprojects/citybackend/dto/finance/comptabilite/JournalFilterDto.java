package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.time.LocalDate;

/**
 * Filtres d'execution de l'edition de journal (B5).
 */
public record JournalFilterDto(
        Long journalId,
        LocalDate dateDebut,
        LocalDate dateFin) {
}
