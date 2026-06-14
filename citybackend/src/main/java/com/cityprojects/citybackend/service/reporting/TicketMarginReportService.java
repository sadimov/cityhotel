package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TicketMarginDto;

import java.time.LocalDate;

/**
 * Rapport R-RES-003 — Ticket moyen et marge par article.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés.</p>
 */
public interface TicketMarginReportService {

    TicketMarginDto computeMargin(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);

    byte[] exportDocx(LocalDate from, LocalDate to);

    byte[] exportPdf(LocalDate from, LocalDate to);
}
