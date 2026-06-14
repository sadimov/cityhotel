package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;

import java.time.LocalDate;

/**
 * Rapport R-FIN-001 — Récap CA.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface CARecapReportService {

    CARecapDto computeCA(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportXlsx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportDocx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportPdf(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);
}
