package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
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
import static org.mockito.Mockito.when;

/**
 * Tests Surefire R-FIN-002 Encours clients (Tour 41 P1).
 */
@ExtendWith(MockitoExtension.class)
class EncoursClientsReportServiceTests {

    @Mock
    private FactureRepository factureRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private EncoursClientsReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EncoursClientsReportServiceImpl(factureRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Facture facture(Long id, LocalDate date, BigDecimal ttc, BigDecimal paye) {
        Facture f = new Facture();
        f.setFactureId(id);
        f.setNumeroFacture("FACT-" + id);
        f.setDateFacture(date);
        f.setMontantTtc(ttc);
        f.setMontantPaye(paye);
        return f;
    }

    @Test
    @DisplayName("T1 - 4 factures distribuees dans 4 buckets")
    void shouldBucketize() {
        LocalDate ref = LocalDate.of(2026, 5, 14);
        Facture f10 = facture(1L, ref.minusDays(10), new BigDecimal("1000"), new BigDecimal("0"));
        Facture f45 = facture(2L, ref.minusDays(45), new BigDecimal("2000"), new BigDecimal("500"));
        Facture f70 = facture(3L, ref.minusDays(70), new BigDecimal("3000"), new BigDecimal("0"));
        Facture f120 = facture(4L, ref.minusDays(120), new BigDecimal("4000"), new BigDecimal("0"));
        when(factureRepository.findFacturesNonSoldees())
                .thenReturn(List.of(f10, f45, f70, f120));

        EncoursClientDto dto = service.computeEncours(ref);

        assertNotNull(dto);
        assertEquals(4, dto.lignes().size());
        assertEquals(0, dto.bucket0_30().compareTo(new BigDecimal("1000")));
        assertEquals(0, dto.bucket30_60().compareTo(new BigDecimal("1500")));
        assertEquals(0, dto.bucket60_90().compareTo(new BigDecimal("3000")));
        assertEquals(0, dto.bucket90Plus().compareTo(new BigDecimal("4000")));
        assertEquals(0, dto.totalEncours().compareTo(new BigDecimal("9500")));
    }

    @Test
    @DisplayName("T2 - aucune facture : buckets zero")
    void shouldHandleEmpty() {
        when(factureRepository.findFacturesNonSoldees()).thenReturn(List.of());

        EncoursClientDto dto = service.computeEncours(LocalDate.of(2026, 5, 14));

        assertEquals(0, dto.totalEncours().compareTo(BigDecimal.ZERO));
        assertEquals(0, dto.lignes().size());
    }

    @Test
    @DisplayName("T3 - reference null defaut now")
    void shouldUseNowByDefault() {
        when(factureRepository.findFacturesNonSoldees()).thenReturn(List.of());

        EncoursClientDto dto = service.computeEncours(null);

        assertNotNull(dto);
        assertNotNull(dto.reference());
    }
}
