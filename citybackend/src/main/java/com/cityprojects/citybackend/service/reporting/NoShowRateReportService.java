package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.NoShowRateDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-HEB-003 — Taux de no-show (Tour 41 P1).
 */
public interface NoShowRateReportService {

    /**
     * Calcule le taux de no-show global + breakdown sur la plage [from, to).
     *
     * @param from    borne inclusive
     * @param to      borne exclusive
     * @param groupBy dimension de breakdown (JOUR / SEMAINE / MOIS)
     */
    NoShowRateDto computeNoShowRate(LocalDate from, LocalDate to, NoShowGroupBy groupBy);

    byte[] exportXlsx(LocalDate from, LocalDate to, NoShowGroupBy groupBy);
}
