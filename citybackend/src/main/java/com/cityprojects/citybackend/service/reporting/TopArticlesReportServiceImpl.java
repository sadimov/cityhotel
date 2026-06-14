package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto.TopArticleLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopArticleProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-RES-002 — Top articles vendus (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TopArticlesReportServiceImpl implements TopArticlesReportService {

    static final int MAX_LIMIT = 200;
    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final LigneCommandeRepository ligneCommandeRepository;
    private final DocumentExportService documentExportService;

    public TopArticlesReportServiceImpl(LigneCommandeRepository ligneCommandeRepository,
                                        DocumentExportService documentExportService) {
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "top-articles",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #limit")
    public TopArticleDto findTopArticles(LocalDate from, LocalDate to, int limit) {
        validate(from, to, limit);

        Instant start = from.atStartOfDay(NOUAKCHOTT).toInstant();
        Instant end = to.atStartOfDay(NOUAKCHOTT).toInstant();
        Pageable pageable = PageRequest.of(0, limit);

        List<TopArticleProjection> projections = ligneCommandeRepository
                .findTopArticles(start, end, pageable);

        List<TopArticleLigneDto> articles = new ArrayList<>(projections.size());
        int rang = 1;
        for (TopArticleProjection p : projections) {
            articles.add(new TopArticleLigneDto(
                    rang++,
                    p.getArticleId(),
                    p.getLibelle(),
                    nz(p.getQuantiteVendue()),
                    nz(p.getCaTotal())));
        }
        return new TopArticleDto(from, to, limit, articles);
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
        TopArticleDto dto = findTopArticles(from, to, limit);
        String title = "Top articles vendus";
        String period = String.format("Période : %s → %s · Limite : %d", from, to, limit);

        BigDecimal caTotal = BigDecimal.ZERO;
        BigDecimal qteTotal = BigDecimal.ZERO;
        for (TopArticleLigneDto a : dto.articles()) {
            caTotal = caTotal.add(a.caTotal() != null ? a.caTotal() : BigDecimal.ZERO);
            qteTotal = qteTotal.add(a.quantiteVendue() != null ? a.quantiteVendue() : BigDecimal.ZERO);
        }

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb articles", String.valueOf(dto.articles().size())),
                new ReportDocument.Kpi("Quantité totale", money(qteTotal)),
                new ReportDocument.Kpi("CA total (cumul)", money(caTotal))
        );

        List<String> headers = List.of("Rang", "Article", "Quantité vendue", "CA TTC");
        List<List<Object>> rows = new ArrayList<>(dto.articles().size());
        for (TopArticleLigneDto a : dto.articles()) {
            rows.add(List.of(
                    a.rang(),
                    a.libelle() != null ? a.libelle() : "",
                    money(a.quantiteVendue()),
                    money(a.caTotal())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Classement", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
