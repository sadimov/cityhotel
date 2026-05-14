package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopSocieteProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-FIN-004 — Top societes (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TopSocietesReportServiceImpl implements TopSocietesReportService {

    static final int MAX_LIMIT = 100;

    private final FactureRepository factureRepository;
    private final XlsxExportService xlsxExportService;

    public TopSocietesReportServiceImpl(FactureRepository factureRepository,
                                        XlsxExportService xlsxExportService) {
        this.factureRepository = factureRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "top-societes",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #limit")
    public List<TopSocieteDto> findTopSocietes(LocalDate from, LocalDate to, int limit) {
        validate(from, to, limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<TopSocieteProjection> projections = factureRepository
                .findTopSocietesByPeriode(from, to, pageable);

        List<TopSocieteDto> result = new ArrayList<>(projections.size());
        int rang = 1;
        for (TopSocieteProjection p : projections) {
            result.add(new TopSocieteDto(
                    rang++,
                    p.getSocieteId(),
                    p.getSocieteNom(),
                    p.getSiret(),
                    nz(p.getNbFactures()),
                    nz(p.getCaTtc()),
                    nz(p.getCaPaye())));
        }
        return result;
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, int limit) {
        List<TopSocieteDto> data = findTopSocietes(from, to, limit);
        List<ColumnSpec<TopSocieteDto>> columns = List.of(
                new ColumnSpec<>("Rang", ColumnType.INTEGER, TopSocieteDto::rang),
                new ColumnSpec<>("Societe", ColumnType.TEXT, TopSocieteDto::societeNom),
                new ColumnSpec<>("SIRET", ColumnType.TEXT, TopSocieteDto::siret),
                new ColumnSpec<>("Nb factures", ColumnType.INTEGER, TopSocieteDto::nbFactures),
                new ColumnSpec<>("CA TTC", ColumnType.MONEY, TopSocieteDto::caTtc),
                new ColumnSpec<>("CA paye", ColumnType.MONEY, TopSocieteDto::caPaye));
        return xlsxExportService.export("Top_Societes", columns, data);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void validate(LocalDate from, LocalDate to, int limit) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException("error.report.limit.outOfRange");
        }
    }
}
