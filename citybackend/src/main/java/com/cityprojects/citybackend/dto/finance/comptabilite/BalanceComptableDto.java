package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultat du calcul de la balance comptable (B5).
 *
 * <p>{@code Sigma soldeDebiteur} doit egaler {@code Sigma soldeCrediteur} ; un
 * ecart au-dela de la tolerance ({@code 0.01} MRU) declenche un log WARN
 * cote service.</p>
 */
public record BalanceComptableDto(
        Long exerciceId,
        String exerciceCode,
        LocalDate dateDebut,
        LocalDate dateFin,
        List<LigneBalanceDto> lignes,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal totalSoldeDebiteur,
        BigDecimal totalSoldeCrediteur,
        Instant generatedAt) {
}
