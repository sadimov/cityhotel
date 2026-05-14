package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TvaRecapDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-FIN-003 — Recap TVA collectee (Tour 41 P1).
 */
public interface TvaCollecteeReportService {

    TvaRecapDto computeTvaRecap(LocalDate from, LocalDate to, TvaGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, TvaGroupBy groupBy);
}
