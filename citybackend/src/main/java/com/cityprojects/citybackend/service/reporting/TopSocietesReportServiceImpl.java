package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopSocieteProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final DocumentExportService documentExportService;

    public TopSocietesReportServiceImpl(FactureRepository factureRepository,
                                        DocumentExportService documentExportService) {
        this.factureRepository = factureRepository;
        this.documentExportService = documentExportService;
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
        return documentExportService.toXlsx(buildDocument(from, to, limit));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to, int limit) {
        return documentExportService.toDocx(buildDocument(from, to, limit));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to, int limit) {
        return documentExportService.toPdf(buildDocument(from, to, limit));
    }

    private ReportDocument buildDocument(LocalDate from, LocalDate to, int limit) {
        List<TopSocieteDto> data = findTopSocietes(from, to, limit);
        String title = "Top sociétés B2B";
        String period = String.format("Période : %s → %s · Limite : %d", from, to, limit);

        BigDecimal caTotal = BigDecimal.ZERO;
        BigDecimal payeTotal = BigDecimal.ZERO;
        long nbFacturesTotal = 0L;
        for (TopSocieteDto s : data) {
            caTotal = caTotal.add(s.caTtc() != null ? s.caTtc() : BigDecimal.ZERO);
            payeTotal = payeTotal.add(s.caPaye() != null ? s.caPaye() : BigDecimal.ZERO);
            nbFacturesTotal += s.nbFactures();
        }

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb sociétés", String.valueOf(data.size())),
                new ReportDocument.Kpi("Nb factures (cumul)", String.valueOf(nbFacturesTotal)),
                new ReportDocument.Kpi("CA TTC (cumul)", money(caTotal)),
                new ReportDocument.Kpi("CA payé (cumul)", money(payeTotal))
        );

        List<String> headers = List.of("Rang", "Société", "SIRET/NIF", "Nb factures", "CA TTC", "CA payé");
        List<List<Object>> rows = new ArrayList<>(data.size());
        for (TopSocieteDto s : data) {
            rows.add(List.of(
                    s.rang(),
                    s.societeNom() != null ? s.societeNom() : "",
                    s.siret() != null ? s.siret() : "",
                    s.nbFactures(),
                    money(s.caTtc()),
                    money(s.caPaye())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Classement par CA", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
