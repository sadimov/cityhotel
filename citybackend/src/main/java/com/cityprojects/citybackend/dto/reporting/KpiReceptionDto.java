package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rapport R-HEB-005 — KPIs operationnels reception pour une date donnee.
 *
 * <p>Vue de pilotage d'une journee : check-in / check-out / walk-in / taux occupation
 * du jour.</p>
 *
 * @param date              date observee
 * @param nbCheckIn         nombre de reservations check-in (statut ARRIVEE) ce jour
 * @param nbCheckOut        nombre de reservations check-out (statut PARTIE) ce jour
 * @param nbWalkIn          reservations creees ET arrivees le meme jour (walk-in)
 * @param nbReservationsActives reservations couvrant le jour (CONFIRMEE ou ARRIVEE)
 * @param nbNoShow          NO_SHOW du jour
 * @param totalChambres     chambres actives du tenant
 * @param nbChambresOccupees nuitees occupees (CONSOMMEE+FACTUREE) sur la date
 * @param tauxOccupationJour 0..100 (2 decimales)
 */
public record KpiReceptionDto(
        LocalDate date,
        Long nbCheckIn,
        Long nbCheckOut,
        Long nbWalkIn,
        Long nbReservationsActives,
        Long nbNoShow,
        Long totalChambres,
        Long nbChambresOccupees,
        BigDecimal tauxOccupationJour
) {
}
