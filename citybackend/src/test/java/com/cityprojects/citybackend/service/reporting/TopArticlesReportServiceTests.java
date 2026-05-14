package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopArticleProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopArticlesReportServiceTests {

    @Mock
    private LigneCommandeRepository ligneCommandeRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private TopArticlesReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopArticlesReportServiceImpl(ligneCommandeRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TopArticleProjection proj(Long id, String lib, BigDecimal qte, BigDecimal ca) {
        return new TopArticleProjection() {
            @Override public Long getArticleId() { return id; }
            @Override public String getLibelle() { return lib; }
            @Override public BigDecimal getQuantiteVendue() { return qte; }
            @Override public BigDecimal getCaTotal() { return ca; }
        };
    }

    @Test
    @DisplayName("T1 - top 3 articles")
    void shouldRankArticles() {
        when(ligneCommandeRepository.findTopArticles(any(), any(), any())).thenReturn(List.of(
                proj(1L, "Riz", new BigDecimal("50"), new BigDecimal("12500")),
                proj(2L, "Poulet", new BigDecimal("30"), new BigDecimal("9000"))));

        TopArticleDto dto = service.findTopArticles(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), 10);

        assertEquals(2, dto.articles().size());
        assertEquals(1, dto.articles().get(0).rang());
        assertEquals("Riz", dto.articles().get(0).libelle());
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findTopArticles(LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 1, 1), 10));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - limit > max")
    void shouldRejectLimit() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findTopArticles(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 1), 999));
        assertEquals("error.report.limit.outOfRange", ex.getMessage());
    }
}
