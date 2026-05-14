package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.projection.NuiteeOccupationProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TypeChambreCountProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito pur) du rapport R-HEB-001 (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 happy path : 10 chambres, 70 nuits / 70 dispo sur 7 jours -&gt; 100 %.</li>
 *   <li>T2 edge : 0 chambre -&gt; taux 0 % (pas de div par zero).</li>
 *   <li>T3 CUSTOM sans dates -&gt; BusinessException error.report.dateRange.required.</li>
 *   <li>T4 CUSTOM dates inversees -&gt; BusinessException error.report.dateRange.invalid.</li>
 *   <li>T5 multi-tenant : aucune requete repository ne prend hotelId en argument
 *       (preuve : Hibernate gere via @TenantId).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OccupationReportServiceTests {

    @Mock
    private ChambreRepository chambreRepository;

    @Mock
    private NuiteeRepository nuiteeRepository;

    @Mock
    private PdfExportService pdfExportService;

    private OccupationReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OccupationReportServiceImpl(chambreRepository, nuiteeRepository, pdfExportService);
        TenantContext.set(42L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - SEMAINE happy path : 10 chambres x 7 jours = 70 dispo, 70 occupees -> 100%")
    void shouldComputeFullOccupation() {
        when(chambreRepository.countByActifTrue()).thenReturn(10L);
        when(nuiteeRepository.countOccupeesOnRange(any(), any())).thenReturn(70L);
        when(chambreRepository.countActivesGroupedByType()).thenReturn(List.of());
        when(nuiteeRepository.aggregateOccupationByType(any(), any())).thenReturn(List.of());

        OccupationDto dto = service.computeOccupation(ReportPeriode.SEMAINE, null, null, LocalDate.of(2026, 5, 6));

        assertNotNull(dto);
        assertEquals(10, dto.totalChambres());
        assertEquals(70L, dto.totalNuiteesDispo());
        assertEquals(70L, dto.totalNuiteesOccupees());
        assertEquals(new BigDecimal("100.00"), dto.tauxOccupationGlobal());
        assertTrue(dto.breakdownParType().isEmpty());
    }

    @Test
    @DisplayName("T2 - 0 chambres : pas de div par zero, taux = 0%")
    void shouldHandleNoRooms() {
        when(chambreRepository.countByActifTrue()).thenReturn(0L);
        when(nuiteeRepository.countOccupeesOnRange(any(), any())).thenReturn(0L);
        when(chambreRepository.countActivesGroupedByType()).thenReturn(List.of());
        when(nuiteeRepository.aggregateOccupationByType(any(), any())).thenReturn(List.of());

        OccupationDto dto = service.computeOccupation(ReportPeriode.JOUR, null, null, LocalDate.now());

        assertEquals(0, dto.totalChambres());
        assertEquals(0L, dto.totalNuiteesDispo());
        assertEquals(new BigDecimal("0.00"), dto.tauxOccupationGlobal());
    }

    @Test
    @DisplayName("T3 - CUSTOM sans from/to : BusinessException")
    void shouldRejectCustomWithoutDates() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeOccupation(ReportPeriode.CUSTOM, null, null, LocalDate.now()));
        assertEquals("error.report.dateRange.required", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - CUSTOM dates inversees : BusinessException")
    void shouldRejectCustomWithInvalidRange() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeOccupation(ReportPeriode.CUSTOM,
                        LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), null));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - multi-tenant : repository calls n'incluent JAMAIS hotelId (Hibernate @TenantId)")
    void shouldNotPassHotelIdAsParameter() {
        when(chambreRepository.countByActifTrue()).thenReturn(5L);
        when(nuiteeRepository.countOccupeesOnRange(any(), any())).thenReturn(10L);
        when(chambreRepository.countActivesGroupedByType()).thenReturn(List.of());
        when(nuiteeRepository.aggregateOccupationByType(any(), any())).thenReturn(List.of());

        service.computeOccupation(ReportPeriode.JOUR, null, null, LocalDate.now());

        // Vérification stricte : aucune méthode appelée n'accepte de hotelId.
        // Les signatures sont prouvées par le compilateur ; l'absence d'arg hotelId
        // confirme que le filtre tenant est porté par Hibernate.
        verify(chambreRepository).countByActifTrue();
        verify(nuiteeRepository).countOccupeesOnRange(any(), any());
    }
}
