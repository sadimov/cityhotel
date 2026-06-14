package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;

import java.time.LocalDate;

/**
 * Rapport R-HEB-005 — KPIs reception jour.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface KpiReceptionReportService {

    KpiReceptionDto computeKpis(LocalDate date);

    byte[] exportXlsx(LocalDate date);

    byte[] exportDocx(LocalDate date);

    byte[] exportPdf(LocalDate date);
}
