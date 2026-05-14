package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.AlosDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosGroupBy;
import com.cityprojects.citybackend.dto.reporting.projection.AlosByTypeProjection;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire R-HEB-002 ALOS (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class AlosReportServiceTests {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private AlosReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlosReportServiceImpl(reservationRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - happy path ALOS par type")
    void shouldReturnAlosByType() {
        when(reservationRepository.aggregateAlosGlobal(any(), any()))
                .thenReturn(new Object[]{12L, 36L});
        AlosByTypeProjection proj = new AlosByTypeProjection() {
            @Override public String getTypeCode() { return "STD"; }
            @Override public String getTypeNom() { return "Standard"; }
            @Override public Long getNbReservations() { return 8L; }
            @Override public Long getTotalNuits() { return 24L; }
        };
        when(reservationRepository.aggregateAlosByType(any(), any())).thenReturn(List.of(proj));

        AlosDto dto = service.computeAlos(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), AlosGroupBy.TYPE_CHAMBRE);

        assertNotNull(dto);
        assertEquals(12L, dto.nbReservations());
        assertEquals(36L, dto.totalNuits());
        assertEquals(1, dto.breakdown().size());
        assertEquals("STD", dto.breakdown().get(0).dimensionKey());
    }

    @Test
    @DisplayName("T2 - dates inversees : BusinessException")
    void shouldRejectInvertedDates() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeAlos(LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1), AlosGroupBy.MOIS));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - groupBy null : BusinessException")
    void shouldRejectNullGroupBy() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeAlos(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1), null));
        assertEquals("error.report.groupBy.required", ex.getMessage());
    }
}
