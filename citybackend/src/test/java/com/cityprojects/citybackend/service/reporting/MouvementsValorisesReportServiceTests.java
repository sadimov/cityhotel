package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.dto.reporting.projection.MouvementValoriseProjection;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MouvementsValorisesReportServiceTests {

    @Mock
    private MouvementStockRepository mouvementStockRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private MouvementsValorisesReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MouvementsValorisesReportServiceImpl(mouvementStockRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static MouvementValoriseProjection proj(Long id, TypeMouvementStock type, int qte,
                                                    BigDecimal prixMouv, BigDecimal prixProd) {
        return new MouvementValoriseProjection() {
            @Override public Long getMouvementId() { return id; }
            @Override public Instant getDate() { return Instant.now(); }
            @Override public Long getProduitId() { return 100L; }
            @Override public String getCodeProduit() { return "P001"; }
            @Override public String getNomProduit() { return "Riz"; }
            @Override public TypeMouvementStock getTypeMouvement() { return type; }
            @Override public Integer getQuantite() { return qte; }
            @Override public BigDecimal getPrixUnitaireMouvement() { return prixMouv; }
            @Override public BigDecimal getPrixUnitaireProduit() { return prixProd; }
            @Override public String getReferenceDocument() { return "BC-001"; }
        };
    }

    @Test
    @DisplayName("T1 - entree + sortie valorises")
    void shouldComputeValorises() {
        when(mouvementStockRepository.findValorisesOnRange(any(), any(), any())).thenReturn(List.of(
                proj(1L, TypeMouvementStock.ENTREE, 10, new BigDecimal("100"), new BigDecimal("100")),
                proj(2L, TypeMouvementStock.SORTIE, 4, null, new BigDecimal("100"))));

        MouvementValoriseDto dto = service.computeMouvements(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), null);

        assertEquals(2L, dto.nbMouvements());
        assertEquals(0, dto.valeurEntrees().compareTo(new BigDecimal("1000")));
        assertEquals(0, dto.valeurSorties().compareTo(new BigDecimal("400")));
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeMouvements(LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 1, 1), null));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucun mouvement")
    void shouldHandleEmpty() {
        when(mouvementStockRepository.findValorisesOnRange(any(), any(), any())).thenReturn(List.of());

        MouvementValoriseDto dto = service.computeMouvements(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), TypeMouvementStock.ENTREE);

        assertEquals(0L, dto.nbMouvements());
    }
}
