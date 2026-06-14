package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TvaRecapDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-FIN-003 — Récap TVA collectée.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface TvaCollecteeReportService {

    TvaRecapDto computeTvaRecap(LocalDate from, LocalDate to, TvaGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, TvaGroupBy groupBy);

    byte[] exportDocx(LocalDate from, LocalDate to, TvaGroupBy groupBy);

    byte[] exportPdf(LocalDate from, LocalDate to, TvaGroupBy groupBy);
}
