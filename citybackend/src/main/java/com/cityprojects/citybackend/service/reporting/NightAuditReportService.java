package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.NightAuditRecapDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Service du rapport R-NA-001 (recap night audit). Tour 40 MVP.
 *
 * <p>Une ligne par jour sur la plage {@code [from, to)}. Si la plage couvre plus de
 * 365 jours, le service refuse (cle i18n {@code error.report.dateRange.tooLarge}).</p>
 */
public interface NightAuditReportService {

    List<NightAuditRecapDto> computeRecap(LocalDate from, LocalDate to);

    byte[] exportPdf(LocalDate from, LocalDate to);
}
