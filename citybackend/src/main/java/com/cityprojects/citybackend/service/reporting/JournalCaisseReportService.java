package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;

import java.time.LocalDate;

/**
 * Rapport R-RES-001 — Journal de caisse du jour.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface JournalCaisseReportService {

    JournalCaisseDto computeJournal(LocalDate date);

    byte[] exportXlsx(LocalDate date);

    byte[] exportDocx(LocalDate date);

    byte[] exportPdf(LocalDate date);
}
