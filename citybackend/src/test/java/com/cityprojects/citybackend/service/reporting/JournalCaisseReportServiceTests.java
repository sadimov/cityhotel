package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;
import com.cityprojects.citybackend.dto.reporting.projection.PaiementModeProjection;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.service.reporting.export.PdfExportService;
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

@ExtendWith(MockitoExtension.class)
class JournalCaisseReportServiceTests {

    @Mock
    private CommandeRepository commandeRepository;
    @Mock
    private PaiementRepository paiementRepository;
    @Mock
    private PdfExportService pdfExportService;
    @Mock
    private XlsxExportService xlsxExportService;

    private JournalCaisseReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JournalCaisseReportServiceImpl(commandeRepository, paiementRepository,
                pdfExportService, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static PaiementModeProjection proj(ModePaiement mode, long nb, BigDecimal mt) {
        return new PaiementModeProjection() {
            @Override public ModePaiement getModePaiement() { return mode; }
            @Override public Long getNbPaiements() { return nb; }
            @Override public BigDecimal getMontantTotal() { return mt; }
        };
    }

    @Test
    @DisplayName("T1 - 2 commandes encaissees, 2 modes paiement")
    void shouldComputeJournal() {
        Commande c1 = new Commande();
        c1.setMontantPaye(new BigDecimal("1500"));
        Commande c2 = new Commande();
        c2.setMontantPaye(new BigDecimal("2500"));
        when(commandeRepository.findEncaisseesBetween(any(), any())).thenReturn(List.of(c1, c2));
        when(paiementRepository.aggregateByModeOnDate(any())).thenReturn(List.of(
                proj(ModePaiement.ESPECES, 1L, new BigDecimal("1500")),
                proj(ModePaiement.CARTE_BANCAIRE, 1L, new BigDecimal("2500"))));

        JournalCaisseDto dto = service.computeJournal(LocalDate.of(2026, 5, 14));

        assertEquals(2L, dto.nbCommandes());
        assertEquals(0, dto.totalRecettes().compareTo(new BigDecimal("4000")));
        assertEquals(2, dto.breakdownModes().size());
    }

    @Test
    @DisplayName("T2 - date null : BusinessException")
    void shouldRejectNullDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeJournal(null));
        assertEquals("error.report.date.required", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucune commande")
    void shouldHandleEmpty() {
        when(commandeRepository.findEncaisseesBetween(any(), any())).thenReturn(List.of());
        when(paiementRepository.aggregateByModeOnDate(any())).thenReturn(List.of());

        JournalCaisseDto dto = service.computeJournal(LocalDate.of(2026, 5, 14));

        assertEquals(0L, dto.nbCommandes());
    }
}
