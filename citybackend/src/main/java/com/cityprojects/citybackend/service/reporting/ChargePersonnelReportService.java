package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;

import java.time.LocalDate;

/**
 * Rapport R-MEN-002 — Charge personnel.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés.</p>
 */
public interface ChargePersonnelReportService {

    ChargePersonnelDto computeCharge(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);

    byte[] exportDocx(LocalDate from, LocalDate to);

    byte[] exportPdf(LocalDate from, LocalDate to);
}
