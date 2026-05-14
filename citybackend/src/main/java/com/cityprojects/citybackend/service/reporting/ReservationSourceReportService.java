package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;

import java.time.LocalDate;

/**
 * Rapport R-HEB-004 — Repartition des reservations par canal (Tour 41 P1).
 */
public interface ReservationSourceReportService {

    ReservationSourceDto computeBySource(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);
}
