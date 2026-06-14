package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaGroupBy;
import com.cityprojects.citybackend.dto.reporting.projection.LigneFactureMonthProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TvaRecapProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-FIN-003 — Recap TVA collectee (Tour 41 P1).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TvaCollecteeReportServiceImpl implements TvaCollecteeReportService {

    private static final DateTimeFormatter MOIS_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LigneFactureRepository ligneFactureRepository;
    private final DocumentExportService documentExportService;

    public TvaCollecteeReportServiceImpl(LigneFactureRepository ligneFactureRepository,
                                         DocumentExportService documentExportService) {
        this.ligneFactureRepository = ligneFactureRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "tva-recap",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #groupBy")
    public TvaRecapDto computeTvaRecap(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        validate(from, to, groupBy);

        Object[] totals = ligneFactureRepository.aggregateTvaTotal(from, to);
        BigDecimal totalHt = toDecimal(totals, 0);
        BigDecimal totalTva = toDecimal(totals, 1);
        BigDecimal totalTtc = toDecimal(totals, 2);

        List<TvaBreakdownDto> breakdown = switch (groupBy) {
            case TAUX -> breakdownByTaux(from, to);
            case MOIS -> breakdownByMonth(from, to);
        };

        return new TvaRecapDto(from, to, groupBy, totalHt, totalTva, totalTtc, breakdown);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        return documentExportService.toXlsx(buildDocument(from, to, groupBy));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        return documentExportService.toDocx(buildDocument(from, to, groupBy));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        return documentExportService.toPdf(buildDocument(from, to, groupBy));
    }

    private ReportDocument buildDocument(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        TvaRecapDto dto = computeTvaRecap(from, to, groupBy);
        String title = "Récapitulatif TVA collectée";
        String period = String.format("Période : %s → %s · Groupage : %s", from, to, groupBy);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Total HT", money(dto.totalHt())),
                new ReportDocument.Kpi("Total TVA", money(dto.totalTva())),
                new ReportDocument.Kpi("Total TTC", money(dto.totalTtc()))
        );

        List<String> headers = List.of("Dimension", "Total HT", "Total TVA", "Total TTC");
        List<List<Object>> rows = new ArrayList<>(dto.breakdown().size());
        for (TvaBreakdownDto b : dto.breakdown()) {
            rows.add(List.of(
                    b.dimensionKey() != null ? b.dimensionKey() : "",
                    money(b.totalHt()),
                    money(b.totalTva()),
                    money(b.totalTtc())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail par " + groupBy, headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<TvaBreakdownDto> breakdownByTaux(LocalDate from, LocalDate to) {
        List<TvaRecapProjection> projections = ligneFactureRepository.aggregateTvaByTaux(from, to);
        List<TvaBreakdownDto> result = new ArrayList<>(projections.size());
        for (TvaRecapProjection p : projections) {
            result.add(new TvaBreakdownDto(
                    "Taux " + (p.getDimension() != null ? p.getDimension() : "0") + " %",
                    nz(p.getTotalHt()),
                    nz(p.getTotalTva()),
                    nz(p.getTotalTtc())));
        }
        return result;
    }

    private List<TvaBreakdownDto> breakdownByMonth(LocalDate from, LocalDate to) {
        List<LigneFactureMonthProjection> projections =
                ligneFactureRepository.findLignesOnRangeWithDate(from, to);
        Map<String, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (LigneFactureMonthProjection p : projections) {
            String key = p.getDateFacture().format(MOIS_FMT);
            BigDecimal[] agg = byMonth.computeIfAbsent(key, k ->
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = agg[0].add(nz(p.getMontantHt()));
            agg[1] = agg[1].add(nz(p.getMontantTva()));
            agg[2] = agg[2].add(nz(p.getMontantTtc()));
        }
        List<TvaBreakdownDto> result = new ArrayList<>(byMonth.size());
        byMonth.forEach((key, agg) -> result.add(new TvaBreakdownDto(key, agg[0], agg[1], agg[2])));
        return result;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal toDecimal(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object v = row[index];
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static void validate(LocalDate from, LocalDate to, TvaGroupBy groupBy) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (groupBy == null) {
            throw new BusinessException("error.report.groupBy.required");
        }
    }
}
