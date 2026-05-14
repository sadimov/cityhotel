package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;

import java.time.LocalDate;

/**
 * Rapport R-RES-001 — Journal de caisse du jour (Tour 41 P2).
 */
public interface JournalCaisseReportService {

    JournalCaisseDto computeJournal(LocalDate date);

    byte[] exportPdf(LocalDate date);

    byte[] exportXlsx(LocalDate date);
}
