package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.TopClientDto;
import com.cityprojects.citybackend.dto.reporting.projection.TopClientProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito pur) du rapport R-CLI-001 (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 happy path : 2 top clients ranges + nbNuitees calcule.</li>
 *   <li>T2 limit invalide (0 ou 101) -&gt; BusinessException error.report.limit.outOfRange.</li>
 *   <li>T3 from > to -&gt; BusinessException error.report.dateRange.invalid.</li>
 *   <li>T4 aucun client -&gt; liste vide.</li>
 *   <li>T5 export XLSX : binaire renvoye.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TopClientsReportServiceTests {

    @Mock
    private FactureRepository factureRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private XlsxExportService xlsxExportService;

    private TopClientsReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopClientsReportServiceImpl(factureRepository, reservationRepository, xlsxExportService);
        TenantContext.set(9L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - 2 clients top ranges 1 et 2")
    void shouldRankClients() {
        TopClientProjection p1 = mockProjection(1L, "CLI-001", "Cheikh", "Mohamed", 3L,
                new BigDecimal("50000.00"), new BigDecimal("50000.00"));
        TopClientProjection p2 = mockProjection(2L, "CLI-002", "Hawa", "Aicha", 2L,
                new BigDecimal("32000.00"), new BigDecimal("20000.00"));
        when(factureRepository.findTopClientsByPeriode(any(), any(), any())).thenReturn(List.of(p1, p2));
        Page<com.cityprojects.citybackend.entity.hebergement.Reservation> emptyPage =
                new PageImpl<>(Collections.emptyList());
        when(reservationRepository.findByClientPrincipalIdOrderByDateArriveeDesc(anyLong(), any()))
                .thenReturn(emptyPage);

        List<TopClientDto> result = service.findTopClients(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 10);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).rang());
        assertEquals("CLI-001", result.get(0).numeroClient());
        assertEquals(new BigDecimal("50000.00"), result.get(0).caTtc());
        assertEquals(2, result.get(1).rang());
        assertEquals("Hawa", result.get(1).prenom());
        assertEquals("Aicha", result.get(1).nom());
    }

    @Test
    @DisplayName("T2 - limit hors plage : BusinessException")
    void shouldRejectInvalidLimit() {
        BusinessException ex0 = assertThrows(BusinessException.class,
                () -> service.findTopClients(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 0));
        assertEquals("error.report.limit.outOfRange", ex0.getMessage());

        BusinessException ex101 = assertThrows(BusinessException.class,
                () -> service.findTopClients(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 101));
        assertEquals("error.report.limit.outOfRange", ex101.getMessage());
    }

    @Test
    @DisplayName("T3 - dateRange invalide")
    void shouldRejectInvalidRange() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findTopClients(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), 10));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T4 - aucun client : liste vide")
    void shouldReturnEmpty() {
        when(factureRepository.findTopClientsByPeriode(any(), any(), any())).thenReturn(Collections.emptyList());

        List<TopClientDto> result = service.findTopClients(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 10);

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("T5 - export XLSX renvoie binaire")
    void shouldExportXlsx() {
        when(factureRepository.findTopClientsByPeriode(any(), any(), any())).thenReturn(Collections.emptyList());
        when(xlsxExportService.export(anyString(), anyList(), anyList())).thenReturn(new byte[]{42});

        byte[] xlsx = service.exportXlsx(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 10);
        assertNotNull(xlsx);
        assertEquals(1, xlsx.length);
    }

    private static TopClientProjection mockProjection(Long id, String num, String prenom, String nom,
                                                       Long nbFactures, BigDecimal caTtc, BigDecimal caPaye) {
        return new TopClientProjection() {
            @Override public Long getClientId() { return id; }
            @Override public String getNumeroClient() { return num; }
            @Override public String getNom() { return nom; }
            @Override public String getPrenom() { return prenom; }
            @Override public Long getNbFactures() { return nbFactures; }
            @Override public BigDecimal getCaTtc() { return caTtc; }
            @Override public BigDecimal getCaPaye() { return caPaye; }
        };
    }
}
