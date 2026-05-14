package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.StockAlertDto;
import com.cityprojects.citybackend.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardDirectionReportServiceTests {

    @Mock
    private OccupationReportService occupationService;
    @Mock
    private CARecapReportService caRecapService;
    @Mock
    private StockAlertReportService stockAlertService;
    @Mock
    private RecapTachesReportService recapTachesService;
    @Mock
    private KpiReceptionReportService kpiReceptionService;

    private DashboardDirectionReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardDirectionReportServiceImpl(occupationService, caRecapService,
                stockAlertService, recapTachesService, kpiReceptionService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - dashboard aggrege")
    void shouldComputeDashboard() {
        LocalDate date = LocalDate.of(2026, 5, 14);
        OccupationDto occ = new OccupationDto(date, date.plusDays(1), 30, 30L, 24L,
                new BigDecimal("80.00"), List.of());
        CARecapDto caJ = new CARecapDto(date, date.plusDays(1), 5L,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                new BigDecimal("80000"), 3L, new BigDecimal("75000"), "MRU");
        CARecapDto caS = new CARecapDto(date.minusDays(6), date.plusDays(1), 20L,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600000"),
                new BigDecimal("550000"), 18L, new BigDecimal("540000"), "MRU");
        KpiReceptionDto kpi = new KpiReceptionDto(date, 5L, 3L, 1L, 24L, 0L, 30L, 24L,
                new BigDecimal("80.00"));

        when(occupationService.computeOccupation(any(ReportPeriode.class), any(), any(), any())).thenReturn(occ);
        when(caRecapService.computeCA(argThat(p -> p == ReportPeriode.JOUR), any(), any(), any())).thenReturn(caJ);
        when(caRecapService.computeCA(argThat(p -> p == ReportPeriode.SEMAINE), any(), any(), any())).thenReturn(caS);
        when(stockAlertService.listStockAlerts()).thenReturn(List.<StockAlertDto>of());
        when(recapTachesService.countTachesEnCours()).thenReturn(7L);
        when(kpiReceptionService.computeKpis(date)).thenReturn(kpi);

        DashboardDirectionDto dto = service.computeDashboard(date);

        assertNotNull(dto);
        assertEquals(5L, dto.nbCheckInJour());
        assertEquals(3L, dto.nbCheckOutJour());
        assertEquals(0, dto.nbAlertesStock());
        assertEquals(7L, dto.nbTachesEnCours());
    }

    @Test
    @DisplayName("T2 - date null : BusinessException")
    void shouldRejectNullDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeDashboard(null));
        assertEquals("error.report.date.required", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - delegations vers les services")
    void shouldDelegateToServices() {
        LocalDate date = LocalDate.of(2026, 5, 14);
        OccupationDto occ = new OccupationDto(date, date.plusDays(1), 0, 0L, 0L,
                BigDecimal.ZERO, List.of());
        CARecapDto ca = new CARecapDto(date, date.plusDays(1), 0L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO, "MRU");
        KpiReceptionDto kpi = new KpiReceptionDto(date, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO);

        when(occupationService.computeOccupation(any(ReportPeriode.class), any(), any(), any())).thenReturn(occ);
        when(caRecapService.computeCA(any(ReportPeriode.class), any(), any(), any())).thenReturn(ca);
        when(stockAlertService.listStockAlerts()).thenReturn(List.<StockAlertDto>of());
        when(recapTachesService.countTachesEnCours()).thenReturn(0L);
        when(kpiReceptionService.computeKpis(date)).thenReturn(kpi);

        DashboardDirectionDto dto = service.computeDashboard(date);

        assertNotNull(dto);
        assertEquals(0L, dto.nbCheckInJour());
    }
}
