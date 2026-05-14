package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;
import com.cityprojects.citybackend.dto.reporting.projection.RotationProduitProjection;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.BonCommandeRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
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
class BcPendantsRotationReportServiceTests {

    @Mock
    private BonCommandeRepository bonCommandeRepository;
    @Mock
    private MouvementStockRepository mouvementStockRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private BcPendantsRotationReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BcPendantsRotationReportServiceImpl(bonCommandeRepository,
                mouvementStockRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - 2 BC pendants")
    void shouldListBcPendants() {
        BonCommande bc1 = new BonCommande();
        bc1.setBonCommandeId(1L);
        bc1.setNumeroBc("BC-001");
        bc1.setFournisseurId(10L);
        bc1.setStatut(StatutBonCommande.ENVOYE);
        bc1.setDateCommande(LocalDate.now().minusDays(15));
        bc1.setMontantTotal(new BigDecimal("5000"));
        when(bonCommandeRepository.findPendants()).thenReturn(List.of(bc1));

        List<BcPendantDto> result = service.findBcPendants();

        assertEquals(1, result.size());
        assertEquals("BC-001", result.get(0).numeroBc());
        assertEquals(15, result.get(0).ageJours());
    }

    @Test
    @DisplayName("T2 - rotation : sortie 10 / stock 20 = 0.50")
    void shouldComputeRotation() {
        RotationProduitProjection proj = new RotationProduitProjection() {
            @Override public Long getProduitId() { return 1L; }
            @Override public String getCodeProduit() { return "P001"; }
            @Override public String getNomProduit() { return "Riz"; }
            @Override public Long getTotalSorties() { return 10L; }
            @Override public Integer getStockActuel() { return 20; }
        };
        when(mouvementStockRepository.aggregateRotation(any(), any())).thenReturn(List.of(proj));

        List<RotationProduitDto> result = service.computeRotation(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).rotation().compareTo(new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("T3 - dates invalides rotation")
    void shouldRejectInvalidRotation() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeRotation(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }
}
