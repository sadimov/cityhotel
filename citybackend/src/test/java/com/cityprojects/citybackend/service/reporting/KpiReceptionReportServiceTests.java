package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire R-HEB-005 KPIs reception (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class KpiReceptionReportServiceTests {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ChambreRepository chambreRepository;
    @Mock
    private NuiteeRepository nuiteeRepository;
    @Mock
    private PdfExportService pdfExportService;

    private KpiReceptionReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KpiReceptionReportServiceImpl(reservationRepository, chambreRepository,
                nuiteeRepository, pdfExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - KPIs simples")
    void shouldComputeKpis() {
        LocalDate date = LocalDate.of(2026, 5, 14);
        when(reservationRepository.countCheckInOnDate(date)).thenReturn(5L);
        when(reservationRepository.countCheckOutOnDate(date)).thenReturn(3L);
        when(reservationRepository.countNoShowOnDate(date)).thenReturn(1L);
        when(reservationRepository.countActivesAtDate(date)).thenReturn(20L);
        when(chambreRepository.countByActifTrue()).thenReturn(30L);
        when(nuiteeRepository.countOccupeesOnRange(any(), any())).thenReturn(25L);
        when(reservationRepository.findArriveesOnDate(date)).thenReturn(List.of());

        KpiReceptionDto dto = service.computeKpis(date);

        assertNotNull(dto);
        assertEquals(5L, dto.nbCheckIn());
        assertEquals(3L, dto.nbCheckOut());
        assertEquals(30L, dto.totalChambres());
        assertEquals(0, dto.tauxOccupationJour().compareTo(new java.math.BigDecimal("83.33")));
    }

    @Test
    @DisplayName("T2 - date null : BusinessException")
    void shouldRejectNullDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeKpis(null));
        assertEquals("error.report.date.required", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucune chambre : taux 0")
    void shouldHandleZeroChambres() {
        LocalDate date = LocalDate.of(2026, 5, 14);
        when(reservationRepository.countCheckInOnDate(date)).thenReturn(0L);
        when(reservationRepository.countCheckOutOnDate(date)).thenReturn(0L);
        when(reservationRepository.countNoShowOnDate(date)).thenReturn(0L);
        when(reservationRepository.countActivesAtDate(date)).thenReturn(0L);
        when(chambreRepository.countByActifTrue()).thenReturn(0L);
        when(nuiteeRepository.countOccupeesOnRange(any(), any())).thenReturn(0L);
        when(reservationRepository.findArriveesOnDate(date)).thenReturn(List.of());

        KpiReceptionDto dto = service.computeKpis(date);

        assertEquals(0, dto.tauxOccupationJour().compareTo(java.math.BigDecimal.ZERO));
    }
}
