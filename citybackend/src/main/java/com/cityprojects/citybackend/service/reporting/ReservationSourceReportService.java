package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;

import java.time.LocalDate;

/**
 * Rapport R-HEB-004 — Repartition des reservations par canal (Tour 41 P1).
 *
 * <p>Tour 51ter : exports Excel / Word / PDF via {@code DocumentExportService}
 * (tableaux bordurés sur les 3 formats, consigne user 2026-06-13).</p>
 */
public interface ReservationSourceReportService {

    ReservationSourceDto computeBySource(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);

    byte[] exportDocx(LocalDate from, LocalDate to);

    byte[] exportPdf(LocalDate from, LocalDate to);
}
