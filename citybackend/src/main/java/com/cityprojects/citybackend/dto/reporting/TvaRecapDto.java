package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-FIN-003 — Recap TVA collectee sur une periode.
 *
 * <p>Source : {@code SUM(lignes_factures.montantTva)} groupe par {@code taux_tva}
 * et/ou par mois (selon dimension).</p>
 *
 * @param from        borne inclusive
 * @param to          borne exclusive
 * @param groupBy     dimension de breakdown (MOIS / TAUX)
 * @param totalHt     somme HT
 * @param totalTva    somme TVA
 * @param totalTtc    somme TTC
 * @param breakdown   detail par dimension
 */
public record TvaRecapDto(
        LocalDate from,
        LocalDate to,
        TvaGroupBy groupBy,
        BigDecimal totalHt,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        List<TvaBreakdownDto> breakdown
) {

    public enum TvaGroupBy {
        /** Groupage par mois calendaire (year-month). */
        MOIS,
        /** Groupage par taux de TVA (0%, 5%, 10%, ...). */
        TAUX
    }

    /**
     * Ligne de breakdown TVA.
     */
    public record TvaBreakdownDto(
            String dimensionKey,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc
    ) {
    }
}
