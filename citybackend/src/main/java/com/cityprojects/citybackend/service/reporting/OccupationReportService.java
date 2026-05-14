package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;

import java.time.LocalDate;

/**
 * Service du rapport R-HEB-001 (occupation chambres). Tour 40 MVP.
 */
public interface OccupationReportService {

    /**
     * Calcule l'occupation sur une periode.
     *
     * @param periode    enum (JOUR / SEMAINE / MOIS / TRIMESTRE / ANNEE / CUSTOM)
     * @param from       borne inclusive si {@code periode == CUSTOM}, sinon ignore
     * @param to         borne exclusive si {@code periode == CUSTOM}, sinon ignore
     * @param reference  date de reference (default {@link LocalDate#now()}) pour les periodes derivees
     */
    OccupationDto computeOccupation(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    /** Variante PDF (binaire). */
    byte[] exportPdf(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);
}
