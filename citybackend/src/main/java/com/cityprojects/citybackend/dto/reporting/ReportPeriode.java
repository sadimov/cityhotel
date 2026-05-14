package com.cityprojects.citybackend.dto.reporting;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Periodicite usuelle d'un rapport (Tour 40 MVP P0).
 *
 * <p>Les bornes effectives ([from, to)) sont resolues par {@link #resolve(LocalDate)} :
 * <ul>
 *   <li>{@code JOUR} : 1 jour ([today, today+1))</li>
 *   <li>{@code SEMAINE} : lundi -&gt; lundi+7</li>
 *   <li>{@code MOIS} : 1er du mois -&gt; 1er du mois suivant</li>
 *   <li>{@code TRIMESTRE} : debut trimestre -&gt; debut trimestre suivant</li>
 *   <li>{@code ANNEE} : 1er janvier -&gt; 1er janvier suivant</li>
 *   <li>{@code CUSTOM} : bornes fournies par l'appelant (jamais resolues ici)</li>
 * </ul>
 */
public enum ReportPeriode {
    JOUR,
    SEMAINE,
    MOIS,
    TRIMESTRE,
    ANNEE,
    CUSTOM;

    /**
     * Resout la periode autour de la date {@code reference} (inclusive debut, exclusive fin).
     *
     * @throws IllegalArgumentException si {@code this} == CUSTOM (l'appelant doit fournir les bornes).
     */
    public DateRange resolve(LocalDate reference) {
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        return switch (this) {
            case JOUR -> new DateRange(reference, reference.plusDays(1));
            case SEMAINE -> {
                LocalDate lundi = reference.with(java.time.DayOfWeek.MONDAY);
                yield new DateRange(lundi, lundi.plusDays(7));
            }
            case MOIS -> {
                LocalDate debut = reference.with(TemporalAdjusters.firstDayOfMonth());
                yield new DateRange(debut, debut.plusMonths(1));
            }
            case TRIMESTRE -> {
                int qStartMonth = ((reference.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate debut = LocalDate.of(reference.getYear(), qStartMonth, 1);
                yield new DateRange(debut, debut.plusMonths(3));
            }
            case ANNEE -> {
                LocalDate debut = reference.with(TemporalAdjusters.firstDayOfYear());
                yield new DateRange(debut, debut.plusYears(1));
            }
            case CUSTOM -> throw new IllegalArgumentException(
                    "CUSTOM periode requires explicit from/to bounds — call DateRangeRequest instead");
        };
    }

    /** Tuple immuable [from, to) ; semantique de plage demi-ouverte. */
    public record DateRange(LocalDate from, LocalDate to) {
        public DateRange {
            if (from == null || to == null) {
                throw new IllegalArgumentException("from/to must not be null");
            }
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("from must be strictly before to");
            }
        }
    }
}
