package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.NoShowRateDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-HEB-003 — Taux de no-show.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface NoShowRateReportService {

    NoShowRateDto computeNoShowRate(LocalDate from, LocalDate to, NoShowGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, NoShowGroupBy groupBy);

    byte[] exportDocx(LocalDate from, LocalDate to, NoShowGroupBy groupBy);

    byte[] exportPdf(LocalDate from, LocalDate to, NoShowGroupBy groupBy);
}
