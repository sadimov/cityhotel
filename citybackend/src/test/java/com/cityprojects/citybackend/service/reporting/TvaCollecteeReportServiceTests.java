package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaGroupBy;
import com.cityprojects.citybackend.dto.reporting.projection.TvaRecapProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
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
 * Tests Surefire R-FIN-003 TVA collectee (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class TvaCollecteeReportServiceTests {

    @Mock
    private LigneFactureRepository ligneFactureRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private TvaCollecteeReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TvaCollecteeReportServiceImpl(ligneFactureRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TvaRecapProjection proj(String dim, BigDecimal ht, BigDecimal tva, BigDecimal ttc) {
        return new TvaRecapProjection() {
            @Override public String getDimension() { return dim; }
            @Override public BigDecimal getTotalHt() { return ht; }
            @Override public BigDecimal getTotalTva() { return tva; }
            @Override public BigDecimal getTotalTtc() { return ttc; }
        };
    }

    @Test
    @DisplayName("T1 - TVA par taux : 2 lignes")
    void shouldGroupByTaux() {
        when(ligneFactureRepository.aggregateTvaTotal(any(), any()))
                .thenReturn(new Object[]{new BigDecimal("10000"), new BigDecimal("0"), new BigDecimal("10000")});
        when(ligneFactureRepository.aggregateTvaByTaux(any(), any())).thenReturn(List.of(
                proj("0", new BigDecimal("8000"), BigDecimal.ZERO, new BigDecimal("8000")),
                proj("16", new BigDecimal("2000"), new BigDecimal("320"), new BigDecimal("2320"))));

        TvaRecapDto dto = service.computeTvaRecap(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), TvaGroupBy.TAUX);

        assertEquals(2, dto.breakdown().size());
        assertEquals(0, dto.totalHt().compareTo(new BigDecimal("10000")));
    }

    @Test
    @DisplayName("T2 - dates invalides : BusinessException")
    void shouldRejectInvalidDates() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeTvaRecap(LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1), TvaGroupBy.TAUX));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucun resultat : totaux zero")
    void shouldHandleEmpty() {
        when(ligneFactureRepository.aggregateTvaTotal(any(), any()))
                .thenReturn(new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        when(ligneFactureRepository.aggregateTvaByTaux(any(), any())).thenReturn(List.of());

        TvaRecapDto dto = service.computeTvaRecap(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), TvaGroupBy.TAUX);

        assertEquals(0, dto.totalHt().compareTo(BigDecimal.ZERO));
    }
}
