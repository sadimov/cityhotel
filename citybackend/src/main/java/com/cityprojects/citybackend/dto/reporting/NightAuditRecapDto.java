package com.cityprojects.citybackend.dto.reporting;

import java.time.LocalDate;

/**
 * Rapport R-NA-001 — Recap night audit jour par jour sur une plage.
 *
 * <p>Une ligne = un jour. Le night audit est traite a midi (cf. {@code regles_night_audit.txt})
 * et son recap est ce DTO. Sources : {@code reservations} (no-show) et {@code nuitees} (generees).</p>
 *
 * @param dateAudit              jour audite
 * @param nbReservationsActives  reservations CONFIRMEE/ARRIVEE sur cette date
 * @param nbNoShow               reservations passees a NO_SHOW lors de l'audit de la date
 * @param nbNuiteesGenerees      nuitees creees/regenerees lors de l'audit
 * @param nbNuiteesConsommees    nuitees CONSOMMEE pour cette date
 * @param ecarts                 nbReservationsActives - (nbNuiteesGenerees + nbNoShow). 0 = audit propre.
 */
public record NightAuditRecapDto(
        LocalDate dateAudit,
        Long nbReservationsActives,
        Long nbNoShow,
        Long nbNuiteesGenerees,
        Long nbNuiteesConsommees,
        Long ecarts
) {
}
