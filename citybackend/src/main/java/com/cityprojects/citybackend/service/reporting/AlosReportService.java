package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.AlosDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosGroupBy;

import java.time.LocalDate;

/**
 * Rapport R-HEB-002 — Average Length of Stay (Tour 41 P1).
 */
public interface AlosReportService {

    /**
     * Calcule l'ALOS global + breakdown sur la plage [from, to).
     *
     * @param from    borne inclusive
     * @param to      borne exclusive
     * @param groupBy dimension de breakdown (TYPE_CHAMBRE ou MOIS)
     */
    AlosDto computeAlos(LocalDate from, LocalDate to, AlosGroupBy groupBy);

    /** Export XLSX du rapport (Apache POI). */
    byte[] exportXlsx(LocalDate from, LocalDate to, AlosGroupBy groupBy);
}
