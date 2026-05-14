package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-FIN-004 — Top societes par CA (B2B, Tour 41 P2).
 */
public interface TopSocietesReportService {

    List<TopSocieteDto> findTopSocietes(LocalDate from, LocalDate to, int limit);

    byte[] exportXlsx(LocalDate from, LocalDate to, int limit);
}
