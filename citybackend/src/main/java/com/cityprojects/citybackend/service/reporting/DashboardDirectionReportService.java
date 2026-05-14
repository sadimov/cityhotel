package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;

import java.time.LocalDate;

/**
 * Rapport R-DIR-001 — Dashboard direction (Tour 41 P2).
 */
public interface DashboardDirectionReportService {

    DashboardDirectionDto computeDashboard(LocalDate date);
}
