package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowGroupBy;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire R-HEB-003 No-show rate (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class NoShowRateReportServiceTests {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private NoShowRateReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoShowRateReportServiceImpl(reservationRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - taux global 25% (1 no-show / 4)")
    void shouldComputeRate() {
        when(reservationRepository.aggregateNoShowGlobal(any(), any()))
                .thenReturn(new Object[]{4L, 1L});
        Reservation r1 = new Reservation();
        r1.setDateArrivee(LocalDate.of(2026, 5, 1));
        r1.setStatut(StatutReservation.NO_SHOW);
        Reservation r2 = new Reservation();
        r2.setDateArrivee(LocalDate.of(2026, 5, 1));
        r2.setStatut(StatutReservation.ARRIVEE);
        when(reservationRepository.findAllArrivantBetween(any(), any()))
                .thenReturn(List.of(r1, r2));

        NoShowRateDto dto = service.computeNoShowRate(LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1), NoShowGroupBy.JOUR);

        assertEquals(4L, dto.totalReservations());
        assertEquals(1L, dto.nbNoShow());
        assertEquals(0, dto.tauxNoShowGlobal().compareTo(new java.math.BigDecimal("25.00")));
        assertTrue(dto.breakdown().size() >= 1);
    }

    @Test
    @DisplayName("T2 - dates inversees : BusinessException")
    void shouldRejectInvertedDates() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeNoShowRate(LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1), NoShowGroupBy.JOUR));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - total 0 : taux 0")
    void shouldHandleEmpty() {
        when(reservationRepository.aggregateNoShowGlobal(any(), any()))
                .thenReturn(new Object[]{0L, 0L});
        when(reservationRepository.findAllArrivantBetween(any(), any())).thenReturn(List.of());

        NoShowRateDto dto = service.computeNoShowRate(LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1), NoShowGroupBy.MOIS);

        assertEquals(0L, dto.totalReservations());
        assertEquals(0L, dto.nbNoShow());
        assertEquals(0, dto.tauxNoShowGlobal().compareTo(java.math.BigDecimal.ZERO));
    }
}
