package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultat du calcul du grand livre (B5).
 */
public record GrandLivreDto(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<CompteGrandLivreDto> comptes,
        Instant generatedAt) {
}
