package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.TopClientDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Service du rapport R-CLI-001 (top clients). Tour 40 MVP.
 */
public interface TopClientsReportService {

    /**
     * Top {@code limit} clients sur la plage [from, to) par CA TTC decroissant.
     *
     * @param limit entre 1 et 100 (sinon error.report.limit.outOfRange)
     */
    List<TopClientDto> findTopClients(LocalDate from, LocalDate to, int limit);

    byte[] exportXlsx(LocalDate from, LocalDate to, int limit);
}
