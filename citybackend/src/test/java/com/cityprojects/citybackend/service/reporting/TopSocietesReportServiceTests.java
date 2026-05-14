package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopSocieteProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
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

/**
 * Tests Surefire R-FIN-004 Top societes (Tour 41 P2).
 */
@ExtendWith(MockitoExtension.class)
class TopSocietesReportServiceTests {

    @Mock
    private FactureRepository factureRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private TopSocietesReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopSocietesReportServiceImpl(factureRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TopSocieteProjection proj(Long id, String nom, Long nb, BigDecimal ca) {
        return new TopSocieteProjection() {
            @Override public Long getSocieteId() { return id; }
            @Override public String getSocieteNom() { return nom; }
            @Override public String getSiret() { return "SIRET-" + id; }
            @Override public Long getNbFactures() { return nb; }
            @Override public BigDecimal getCaTtc() { return ca; }
            @Override public BigDecimal getCaPaye() { return ca; }
        };
    }

    @Test
    @DisplayName("T1 - 3 societes, ranking")
    void shouldRankSocietes() {
        when(factureRepository.findTopSocietesByPeriode(any(), any(), any())).thenReturn(List.of(
                proj(10L, "Alpha SARL", 12L, new BigDecimal("50000")),
                proj(20L, "Beta SA", 8L, new BigDecimal("30000"))));

        List<TopSocieteDto> top = service.findTopSocietes(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), 10);

        assertEquals(2, top.size());
        assertEquals(1, top.get(0).rang());
        assertEquals("Alpha SARL", top.get(0).societeNom());
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findTopSocietes(LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 1, 1), 10));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - limit hors bornes")
    void shouldRejectBadLimit() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findTopSocietes(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 1), 0));
        assertEquals("error.report.limit.outOfRange", ex.getMessage());
    }
}
