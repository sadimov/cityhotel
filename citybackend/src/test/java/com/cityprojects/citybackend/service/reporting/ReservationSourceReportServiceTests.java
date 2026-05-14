package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;
import com.cityprojects.citybackend.dto.reporting.projection.ReservationSourceProjection;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire R-HEB-004 Sources reservations (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class ReservationSourceReportServiceTests {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private ReservationSourceReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReservationSourceReportServiceImpl(reservationRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ReservationSourceProjection proj(String canal, long nb, BigDecimal ca) {
        return new ReservationSourceProjection() {
            @Override public String getSourceCanal() { return canal; }
            @Override public Long getNbReservations() { return nb; }
            @Override public BigDecimal getCaMontant() { return ca; }
        };
    }

    @Test
    @DisplayName("T1 - 3 canaux, distribution OK")
    void shouldDistributeBySource() {
        when(reservationRepository.aggregateBySourceCanal(any(), any())).thenReturn(List.of(
                proj("DIRECT_HOTEL", 6L, new BigDecimal("60000")),
                proj("BOOKING_COM", 3L, new BigDecimal("30000")),
                proj("WALK_IN", 1L, new BigDecimal("10000"))));
        when(reservationRepository.sumMontantTotalOnRange(any(), any()))
                .thenReturn(new BigDecimal("100000"));

        ReservationSourceDto dto = service.computeBySource(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(10L, dto.totalReservations());
        assertEquals(3, dto.breakdown().size());
        assertEquals("DIRECT_HOTEL", dto.breakdown().get(0).sourceCanal());
        assertEquals(0, dto.breakdown().get(0).pourcentage().compareTo(new BigDecimal("60.00")));
    }

    @Test
    @DisplayName("T2 - source NULL convertie en NON_RENSEIGNE")
    void shouldMapNullSource() {
        when(reservationRepository.aggregateBySourceCanal(any(), any())).thenReturn(List.of(
                proj(null, 5L, new BigDecimal("5000"))));
        when(reservationRepository.sumMontantTotalOnRange(any(), any()))
                .thenReturn(new BigDecimal("5000"));

        ReservationSourceDto dto = service.computeBySource(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(1, dto.breakdown().size());
        assertEquals(ReservationSourceReportServiceImpl.NON_RENSEIGNE,
                dto.breakdown().get(0).sourceCanal());
    }

    @Test
    @DisplayName("T3 - dates invalides : BusinessException")
    void shouldValidateRange() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeBySource(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1)));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }
}
