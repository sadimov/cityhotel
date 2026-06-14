package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;

import java.time.LocalDate;

/**
 * Rapport R-FIN-002 — Encours clients.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface EncoursClientsReportService {

    EncoursClientDto computeEncours(LocalDate reference);

    byte[] exportXlsx(LocalDate reference);

    byte[] exportDocx(LocalDate reference);

    byte[] exportPdf(LocalDate reference);
}
