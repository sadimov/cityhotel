package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;

import java.time.LocalDate;

/**
 * Rapport R-DIR-001 — Dashboard direction.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface DashboardDirectionReportService {

    DashboardDirectionDto computeDashboard(LocalDate date);

    byte[] exportXlsx(LocalDate date);

    byte[] exportDocx(LocalDate date);

    byte[] exportPdf(LocalDate date);
}
