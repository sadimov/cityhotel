package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto.TopArticleLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopArticleProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final XlsxExportService xlsxExportService;

    public TopArticlesReportServiceImpl(LigneCommandeRepository ligneCommandeRepository,
                                        XlsxExportService xlsxExportService) {
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.xlsxExportService = xlsxExportService;
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
        TopArticleDto dto = findTopArticles(from, to, limit);
        List<ColumnSpec<TopArticleLigneDto>> columns = List.of(
                new ColumnSpec<>("Rang", ColumnType.INTEGER, TopArticleLigneDto::rang),
                new ColumnSpec<>("Article", ColumnType.TEXT, TopArticleLigneDto::libelle),
                new ColumnSpec<>("Quantite vendue", ColumnType.DECIMAL, TopArticleLigneDto::quantiteVendue),
                new ColumnSpec<>("CA TTC", ColumnType.MONEY, TopArticleLigneDto::caTotal));
        return xlsxExportService.export("Top_Articles", columns, dto.articles());
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
