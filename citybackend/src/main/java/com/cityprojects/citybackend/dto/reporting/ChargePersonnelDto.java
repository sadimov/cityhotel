package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-MEN-002 — Charge de travail par personnel (Tour 41 P2).
 *
 * <p>Calcule la duree reelle totale via {@code heureFinReelle - heureDebutReelle}
 * et le ratio assignees/terminees par personnel.</p>
 */
public record ChargePersonnelDto(
        LocalDate from,
        LocalDate to,
        List<ChargeLigneDto> personnels
) {

    /**
     * Ligne de charge pour un personnel.
     *
     * @param personnelId      FK
     * @param nbAssignees      nb taches assignees sur la plage
     * @param nbTerminees      nb dans statut TERMINEE
     * @param dureeTotaleMin   somme (heureFinReelle - heureDebutReelle) en minutes
     * @param tauxCompletion   pct termine / assigne (2 decimales)
     */
    public record ChargeLigneDto(
            Long personnelId,
            Long nbAssignees,
            Long nbTerminees,
            Long dureeTotaleMin,
            BigDecimal tauxCompletion
    ) {
    }
}
