package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;

import java.time.LocalDate;

/**
 * Rapport R-MEN-002 — Charge personnel (Tour 41 P2).
 */
public interface ChargePersonnelReportService {

    ChargePersonnelDto computeCharge(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);
}
