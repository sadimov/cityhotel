package com.cityprojects.citybackend.dto.reporting;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-MEN-001 — Recapitulatif des taches menage sur une periode.
 *
 * <p>Source : {@code menage.taches}. Groupage : JOUR, TYPE_TACHE ou STATUT.</p>
 *
 * @param from        borne inclusive
 * @param to          borne exclusive
 * @param groupBy     dimension de breakdown
 * @param totalTaches total sur la plage
 * @param breakdown   detail par dimension
 */
public record RecapTacheDto(
        LocalDate from,
        LocalDate to,
        TacheGroupBy groupBy,
        Long totalTaches,
        List<RecapBreakdownDto> breakdown
) {

    public enum TacheGroupBy {
        JOUR,
        TYPE_TACHE,
        STATUT
    }

    public record RecapBreakdownDto(
            String dimensionKey,
            Long nbTaches
    ) {
    }
}
