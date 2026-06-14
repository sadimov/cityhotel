package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;

import java.time.LocalDate;

/**
 * Rapport R-HEB-001 (occupation chambres).
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface OccupationReportService {

    OccupationDto computeOccupation(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportXlsx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportDocx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportPdf(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);
}
