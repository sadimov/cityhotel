package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-FIN-004 — Top sociétés par CA (B2B).
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface TopSocietesReportService {

    List<TopSocieteDto> findTopSocietes(LocalDate from, LocalDate to, int limit);

    byte[] exportXlsx(LocalDate from, LocalDate to, int limit);

    byte[] exportDocx(LocalDate from, LocalDate to, int limit);

    byte[] exportPdf(LocalDate from, LocalDate to, int limit);
}
