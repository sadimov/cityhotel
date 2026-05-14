package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-HEB-002 — ALOS (Average Length of Stay) sur une periode.
 *
 * <p>Source : {@code hebergement.reservations} non annulees sur la plage.
 * Calcul : {@code SUM(nb_nuits) / COUNT(reservations)}. Groupage possible
 * par type de chambre ou par mois (cf. {@link AlosGroupBy}).</p>
 *
 * @param from              borne inclusive
 * @param to                borne exclusive
 * @param groupBy           dimension de breakdown
 * @param nbReservations    total des reservations sur la plage (hors ANNULEE)
 * @param totalNuits        somme {@code nb_nuits}
 * @param alosGlobal        moyenne globale (2 decimales)
 * @param breakdown         decoupage par dimension
 */
public record AlosDto(
        LocalDate from,
        LocalDate to,
        AlosGroupBy groupBy,
        Long nbReservations,
        Long totalNuits,
        BigDecimal alosGlobal,
        List<AlosBreakdownDto> breakdown
) {

    /** Dimension de groupage pour le breakdown ALOS. */
    public enum AlosGroupBy {
        /** Groupage par type de chambre (via le premier pivot reservation_chambres). */
        TYPE_CHAMBRE,
        /** Groupage par mois calendaire (year-month). */
        MOIS
    }

    /**
     * Ligne de breakdown ALOS.
     *
     * @param dimensionKey      cle metier (typeCode ou yyyy-MM)
     * @param dimensionLabel    libelle affichable
     * @param nbReservations    nombre de reservations dans la dimension
     * @param totalNuits        somme nuitees
     * @param alos              moyenne (2 decimales)
     */
    public record AlosBreakdownDto(
            String dimensionKey,
            String dimensionLabel,
            Long nbReservations,
            Long totalNuits,
            BigDecimal alos
    ) {
    }
}
