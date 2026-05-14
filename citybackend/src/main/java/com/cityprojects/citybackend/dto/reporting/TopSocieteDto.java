package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;

/**
 * Rapport R-FIN-004 — Top societes par CA (B2B).
 *
 * <p>Tri SQL par {@code SUM(montantTtc) DESC}, limit configurable cote service.</p>
 */
public record TopSocieteDto(
        int rang,
        Long societeId,
        String societeNom,
        String siret,
        Long nbFactures,
        BigDecimal caTtc,
        BigDecimal caPaye
) {
}
