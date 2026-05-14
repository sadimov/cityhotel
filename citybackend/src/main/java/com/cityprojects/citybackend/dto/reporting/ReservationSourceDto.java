package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-HEB-004 — Repartition des reservations par canal de distribution.
 *
 * <p>Source : {@code hebergement.reservations.source_canal} (Tour 41 changeset 044).
 * NULL est regroupe sous "NON_RENSEIGNE" pour l'affichage.</p>
 *
 * @param from              borne inclusive
 * @param to                borne exclusive
 * @param totalReservations total reservations (hors ANNULEE) sur la plage
 * @param caTotal           somme montantTotal sur la plage (informationnel)
 * @param breakdown         decoupage par canal (count + ca + pct)
 */
public record ReservationSourceDto(
        LocalDate from,
        LocalDate to,
        Long totalReservations,
        BigDecimal caTotal,
        List<SourceBreakdownDto> breakdown
) {

    /**
     * Ligne de breakdown par canal.
     *
     * @param sourceCanal       code canal (DIRECT_HOTEL, BOOKING_COM, ... ou NON_RENSEIGNE)
     * @param nbReservations    nombre dans la dimension
     * @param caMontant         somme montantTotal
     * @param pourcentage       part 0..100 sur total reservations
     */
    public record SourceBreakdownDto(
            String sourceCanal,
            Long nbReservations,
            BigDecimal caMontant,
            BigDecimal pourcentage
    ) {
    }
}
