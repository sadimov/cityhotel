package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TicketMarginDto;

import java.time.LocalDate;

/**
 * Rapport R-RES-003 — Ticket moyen et marge par article (Tour 41 P2).
 */
public interface TicketMarginReportService {

    TicketMarginDto computeMargin(LocalDate from, LocalDate to);

    byte[] exportXlsx(LocalDate from, LocalDate to);
}
