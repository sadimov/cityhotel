package com.cityprojects.citybackend.dto.reporting;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Plage de dates [from, to) fournie par l'appelant pour les rapports CUSTOM.
 *
 * <p>Validation Bean Validation cote controller : {@code from} et {@code to} obligatoires.
 * La regle {@code from &lt;= to} est verifiee par le service (cle i18n {@code error.report.dateRange.invalid}).</p>
 *
 * <p>Convention : {@code to} est <b>exclusif</b> (demi-ouvert) — pour un rapport
 * "lundi a dimanche", passer {@code from=lundi, to=lundi suivant}.</p>
 */
public record DateRangeRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to
) {
}
