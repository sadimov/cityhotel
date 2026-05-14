package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.util.List;

/**
 * Section grand livre pour un compte (B5).
 *
 * <p>{@code reportInitial} : solde du compte au {@code dateDebut - 1 jour}
 * calcule a partir du debut de l'exercice. Convention : positif si debiteur,
 * negatif si crediteur (independant du sens normal).</p>
 *
 * <p>{@code soldeFinal} : {@code reportInitial + Sigma debits - Sigma credits}.</p>
 */
public record CompteGrandLivreDto(
        String compteCode,
        String compteLibelle,
        BigDecimal reportInitial,
        List<LigneGrandLivreDto> lignes,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal soldeFinal) {
}
