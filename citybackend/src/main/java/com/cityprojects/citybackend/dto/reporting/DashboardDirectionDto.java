package com.cityprojects.citybackend.dto.reporting;

import java.time.LocalDate;

/**
 * Rapport R-DIR-001 — Dashboard direction (Tour 41 P2).
 *
 * <p>Vue agregee temps reel : occupation du jour, CA jour + semaine,
 * alertes stock, taches en cours, check-in/out du jour. Pas d'export — vue UI.</p>
 */
public record DashboardDirectionDto(
        LocalDate date,
        OccupationDto occupation,
        CARecapDto caJour,
        CARecapDto caSemaine,
        Integer nbAlertesStock,
        Long nbTachesEnCours,
        Long nbCheckInJour,
        Long nbCheckOutJour
) {
}
