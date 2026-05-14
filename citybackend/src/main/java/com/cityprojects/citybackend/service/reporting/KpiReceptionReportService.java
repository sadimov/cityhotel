package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;

import java.time.LocalDate;

/**
 * Rapport R-HEB-005 — KPIs reception jour (Tour 41 P1).
 */
public interface KpiReceptionReportService {

    KpiReceptionDto computeKpis(LocalDate date);

    byte[] exportPdf(LocalDate date);
}
