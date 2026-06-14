package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.AlosDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-HEB-002 — Average Length of Stay.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface AlosReportService {

    AlosDto computeAlos(LocalDate from, LocalDate to, AlosGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, AlosGroupBy groupBy);

    byte[] exportDocx(LocalDate from, LocalDate to, AlosGroupBy groupBy);

    byte[] exportPdf(LocalDate from, LocalDate to, AlosGroupBy groupBy);
}
