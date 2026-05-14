package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-HEB-003 — Taux de no-show sur une periode.
 *
 * <p>Source : {@code hebergement.reservations}, ratio
 * {@code COUNT(NO_SHOW) / COUNT(toutes les reservations dont la dateArrivee est dans la plage)}.</p>
 *
 * @param from              borne inclusive
 * @param to                borne exclusive
 * @param groupBy           dimension de breakdown (JOUR / SEMAINE / MOIS)
 * @param totalReservations total reservations dont dateArrivee dans [from, to)
 * @param nbNoShow          total reservations statut NO_SHOW
 * @param tauxNoShowGlobal  0..100 (2 decimales)
 * @param breakdown         decoupage par dimension
 */
public record NoShowRateDto(
        LocalDate from,
        LocalDate to,
        NoShowGroupBy groupBy,
        Long totalReservations,
        Long nbNoShow,
        BigDecimal tauxNoShowGlobal,
        List<NoShowBreakdownDto> breakdown
) {

    /** Dimension de groupage pour le breakdown no-show. */
    public enum NoShowGroupBy {
        JOUR,
        SEMAINE,
        MOIS
    }

    /**
     * Ligne de breakdown no-show.
     *
     * @param dimensionKey      cle metier (yyyy-MM-dd, yyyy-Www ou yyyy-MM)
     * @param dimensionLabel    libelle affichable
     * @param totalReservations total dans la dimension
     * @param nbNoShow          NO_SHOW dans la dimension
     * @param taux              0..100 (2 decimales)
     */
    public record NoShowBreakdownDto(
            String dimensionKey,
            String dimensionLabel,
            Long totalReservations,
            Long nbNoShow,
            BigDecimal taux
    ) {
    }
}
