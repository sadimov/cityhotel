package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.NightAuditRecapDto;
import com.cityprojects.citybackend.exception.BusinessException;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito pur) du rapport R-NA-001 (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 happy path : 3 jours -&gt; 3 lignes, calculs corrects.</li>
 *   <li>T2 from = to -&gt; BusinessException error.report.dateRange.invalid.</li>
 *   <li>T3 plage > 366 jours -&gt; BusinessException error.report.dateRange.tooLarge.</li>
 *   <li>T4 export PDF : delegue au PdfExportService.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class NightAuditReportServiceTests {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private NuiteeRepository nuiteeRepository;

    @Mock
    private PdfExportService pdfExportService;

    private NightAuditReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NightAuditReportServiceImpl(reservationRepository, nuiteeRepository, pdfExportService);
        TenantContext.set(11L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - 3 jours, 1 ligne par jour, ecarts calcules")
    void shouldComputeRecapDayByDay() {
        // Each day: 5 actives, 1 no-show, 4 nuitees gen, 4 consommees -> ecarts = 5 - (4+1) = 0
        when(reservationRepository.countActivesAtDate(any())).thenReturn(5L);
        when(reservationRepository.countNoShowOnDate(any())).thenReturn(1L);
        when(nuiteeRepository.countByDateNuit(any())).thenReturn(4L);
        when(nuiteeRepository.countConsommeesByDate(any())).thenReturn(4L);

        List<NightAuditRecapDto> result = service.computeRecap(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4));

        assertEquals(3, result.size());
        for (int i = 0; i < 3; i++) {
            NightAuditRecapDto line = result.get(i);
            assertEquals(LocalDate.of(2026, 5, 1).plusDays(i), line.dateAudit());
            assertEquals(5L, line.nbReservationsActives());
            assertEquals(1L, line.nbNoShow());
            assertEquals(4L, line.nbNuiteesGenerees());
            assertEquals(0L, line.ecarts());
        }
    }

    @Test
    @DisplayName("T2 - from == to : BusinessException")
    void shouldRejectEmptyRange() {
        LocalDate d = LocalDate.of(2026, 5, 1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeRecap(d, d));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - plage > 366 jours : BusinessException")
    void shouldRejectOversizedRange() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);    // ~ 731 jours
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeRecap(from, to));
        assertEquals("error.report.dateRange.tooLarge", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - export PDF : delegue + binaire renvoye")
    void shouldExportPdf() {
        when(reservationRepository.countActivesAtDate(any())).thenReturn(0L);
        when(reservationRepository.countNoShowOnDate(any())).thenReturn(0L);
        when(nuiteeRepository.countByDateNuit(any())).thenReturn(0L);
        when(nuiteeRepository.countConsommeesByDate(any())).thenReturn(0L);
        when(pdfExportService.exportToPdf(anyString(), any(), anyList())).thenReturn(new byte[]{1});

        byte[] pdf = service.exportPdf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2));

        assertNotNull(pdf);
        assertEquals(1, pdf.length);
    }
}
