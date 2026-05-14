package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;

import java.time.LocalDate;

/**
 * Service du rapport R-FIN-001 (CA recap). Tour 40 MVP.
 */
public interface CARecapReportService {

    CARecapDto computeCA(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);

    byte[] exportXlsx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference);
}
