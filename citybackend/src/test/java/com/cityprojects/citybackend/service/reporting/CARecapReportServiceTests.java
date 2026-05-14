package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.projection.CARecapProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito pur) du rapport R-FIN-001 (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 happy path : agg projection -&gt; DTO complet, devise MRU.</li>
 *   <li>T2 edge : agg projection NULL -&gt; valeurs 0.</li>
 *   <li>T3 export XLSX : delegue au XlsxExportService et renvoie le binaire.</li>
 *   <li>T4 CUSTOM dates inversees -&gt; BusinessException.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CARecapReportServiceTests {

    @Mock
    private FactureRepository factureRepository;

    @Mock
    private PaiementRepository paiementRepository;

    @Mock
    private XlsxExportService xlsxExportService;

    private CARecapReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CARecapReportServiceImpl(factureRepository, paiementRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - happy path agg valides")
    void shouldReturnAggregatedCA() {
        CARecapProjection projection = new CARecapProjection() {
            @Override public Long getNbFactures() { return 12L; }
            @Override public BigDecimal getCaEmisHt() { return new BigDecimal("10000.00"); }
            @Override public BigDecimal getCaEmisTva() { return BigDecimal.ZERO; }
            @Override public BigDecimal getCaEmisTtc() { return new BigDecimal("10000.00"); }
            @Override public BigDecimal getCaPayeTtc() { return new BigDecimal("8500.00"); }
        };
        when(factureRepository.aggregateCaOnRange(any(), any())).thenReturn(projection);
        when(paiementRepository.countValidesOnRange(any(), any())).thenReturn(7L);
        when(paiementRepository.sumMontantValidesOnRange(any(), any())).thenReturn(new BigDecimal("7500.00"));

        CARecapDto dto = service.computeCA(ReportPeriode.MOIS, null, null, LocalDate.of(2026, 5, 6));

        assertNotNull(dto);
        assertEquals(12L, dto.nbFactures());
        assertEquals(new BigDecimal("10000.00"), dto.caEmisTtc());
        assertEquals(new BigDecimal("8500.00"), dto.caPayeTtc());
        assertEquals(7L, dto.nbPaiements());
        assertEquals(new BigDecimal("7500.00"), dto.montantEncaisse());
        assertEquals("MRU", dto.devise());
    }

    @Test
    @DisplayName("T2 - agg NULL : valeurs zero")
    void shouldHandleNullAgg() {
        when(factureRepository.aggregateCaOnRange(any(), any())).thenReturn(null);
        when(paiementRepository.countValidesOnRange(any(), any())).thenReturn(0L);
        when(paiementRepository.sumMontantValidesOnRange(any(), any())).thenReturn(null);

        CARecapDto dto = service.computeCA(ReportPeriode.JOUR, null, null, LocalDate.now());

        assertEquals(0L, dto.nbFactures());
        assertEquals(BigDecimal.ZERO, dto.caEmisHt());
        assertEquals(BigDecimal.ZERO, dto.caEmisTtc());
        assertEquals(BigDecimal.ZERO, dto.montantEncaisse());
    }

    @Test
    @DisplayName("T3 - export XLSX : binaire renvoye")
    void shouldExportXlsx() {
        when(factureRepository.aggregateCaOnRange(any(), any())).thenReturn(null);
        when(paiementRepository.countValidesOnRange(any(), any())).thenReturn(0L);
        when(paiementRepository.sumMontantValidesOnRange(any(), any())).thenReturn(null);
        when(xlsxExportService.export(anyString(), anyList(), anyList()))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] xlsx = service.exportXlsx(ReportPeriode.SEMAINE, null, null, LocalDate.now());

        assertNotNull(xlsx);
        assertEquals(3, xlsx.length);
    }

    @Test
    @DisplayName("T4 - CUSTOM dates inversees : BusinessException")
    void shouldRejectInvalidCustomRange() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeCA(ReportPeriode.CUSTOM,
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 10), null));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }
}
