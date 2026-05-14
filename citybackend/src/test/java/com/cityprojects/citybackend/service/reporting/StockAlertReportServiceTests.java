package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.StockAlertDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito pur) du rapport R-INV-001 (Tour 40 MVP).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 happy path : 2 produits sous seuil, 1 CRITIQUE / 1 ALERTE.</li>
 *   <li>T2 edge : aucune alerte -&gt; liste vide.</li>
 *   <li>T3 valeur manquante = ecart * prixUnitaire.</li>
 *   <li>T4 export XLSX renvoie binaire.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StockAlertReportServiceTests {

    @Mock
    private ProduitRepository produitRepository;

    @Mock
    private XlsxExportService xlsxExportService;

    private StockAlertReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StockAlertReportServiceImpl(produitRepository, xlsxExportService);
        TenantContext.set(3L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - 2 produits sous seuil : 1 CRITIQUE / 1 ALERTE")
    void shouldClassifyCritiqueVsAlerte() {
        Produit critique = produit("FARINE", "Farine T55", "kg", 1, 5, 2, new BigDecimal("12.00"));
        Produit alerte = produit("SUCRE", "Sucre blanc", "kg", 4, 5, 2, new BigDecimal("8.00"));
        when(produitRepository.findEnAlerte()).thenReturn(List.of(critique, alerte));

        List<StockAlertDto> result = service.listStockAlerts();

        assertEquals(2, result.size());
        StockAlertDto first = result.get(0);
        assertEquals("FARINE", first.codeProduit());
        assertEquals("CRITIQUE", first.statut());
        assertEquals(4, first.ecart());                                  // 5 - 1
        assertEquals(new BigDecimal("48.00"), first.valeurManquante());  // 4 * 12.00

        StockAlertDto second = result.get(1);
        assertEquals("ALERTE", second.statut());
        assertEquals(1, second.ecart());                                  // 5 - 4
        assertEquals(new BigDecimal("8.00"), second.valeurManquante());
    }

    @Test
    @DisplayName("T2 - aucune alerte : liste vide")
    void shouldReturnEmptyList() {
        when(produitRepository.findEnAlerte()).thenReturn(Collections.emptyList());
        List<StockAlertDto> result = service.listStockAlerts();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("T3 - prixUnitaire null : valeurManquante = 0")
    void shouldHandleNullPrice() {
        Produit p = produit("X", "X", "u", 0, 1, 0, null);
        when(produitRepository.findEnAlerte()).thenReturn(List.of(p));

        List<StockAlertDto> result = service.listStockAlerts();

        assertEquals(BigDecimal.ZERO, result.get(0).valeurManquante());
    }

    @Test
    @DisplayName("T4 - export XLSX delegue + binaire renvoye")
    void shouldExportXlsx() {
        when(produitRepository.findEnAlerte()).thenReturn(Collections.emptyList());
        when(xlsxExportService.export(anyString(), anyList(), anyList())).thenReturn(new byte[]{9});

        byte[] xlsx = service.exportXlsx();
        assertEquals(1, xlsx.length);
    }

    private static Produit produit(String code, String nom, String unite, int stock,
                                   int alerte, int critique, BigDecimal prix) {
        Produit p = new Produit();
        p.setProduitId(System.nanoTime());
        p.setCodeProduit(code);
        p.setNomProduit(nom);
        p.setUniteMesure(unite);
        p.setStockActuel(stock);
        p.setSeuilAlerte(alerte);
        p.setSeuilCritique(critique);
        p.setPrixUnitaire(prix);
        return p;
    }
}
