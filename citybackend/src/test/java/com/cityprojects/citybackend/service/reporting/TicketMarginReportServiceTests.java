package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.TicketMarginDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.RecetteArticleRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketMarginReportServiceTests {

    @Mock
    private CommandeRepository commandeRepository;
    @Mock
    private LigneCommandeRepository ligneCommandeRepository;
    @Mock
    private RecetteArticleRepository recetteArticleRepository;
    @Mock
    private ArticleMenuRepository articleMenuRepository;
    @Mock
    private ProduitRepository produitRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private TicketMarginReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketMarginReportServiceImpl(commandeRepository, ligneCommandeRepository,
                recetteArticleRepository, articleMenuRepository, produitRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - ticket moyen + marge reelle")
    void shouldComputeMargin() {
        when(commandeRepository.aggregateTicketMoyen(any(), any()))
                .thenReturn(new Object[]{10L, new BigDecimal("25000")});

        LigneCommande l1 = new LigneCommande();
        l1.setArticleId(42L);
        when(ligneCommandeRepository.findLignesOnRange(any(), any())).thenReturn(List.of(l1));

        ArticleMenu article = new ArticleMenu();
        article.setArticleId(42L);
        article.setNom("Riz au poulet");
        article.setPrix(new BigDecimal("1500"));
        when(articleMenuRepository.findById(42L)).thenReturn(Optional.of(article));

        RecetteArticle r = new RecetteArticle();
        r.setArticleId(42L);
        r.setProduitId(100L);
        r.setQuantiteParUnite(new BigDecimal("0.150"));
        when(recetteArticleRepository.findByArticleIdAndActifTrue(eq(42L))).thenReturn(List.of(r));

        Produit p = new Produit();
        p.setProduitId(100L);
        p.setPrixUnitaire(new BigDecimal("1000"));
        when(produitRepository.findById(anyLong())).thenReturn(Optional.of(p));

        TicketMarginDto dto = service.computeMargin(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(10L, dto.nbCommandes());
        assertEquals(0, dto.ticketMoyen().compareTo(new BigDecimal("2500.00")));
        assertEquals(1, dto.marges().size());
        // Cout matiere = 0.150 * 1000 = 150 ; marge = 1500 - 150 = 1350
        assertEquals(0, dto.marges().get(0).coutMatiere().compareTo(new BigDecimal("150.00")));
        assertEquals(0, dto.marges().get(0).margeUnitaire().compareTo(new BigDecimal("1350.00")));
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeMargin(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucune commande : ticket 0")
    void shouldHandleEmpty() {
        when(commandeRepository.aggregateTicketMoyen(any(), any()))
                .thenReturn(new Object[]{0L, BigDecimal.ZERO});
        when(ligneCommandeRepository.findLignesOnRange(any(), any())).thenReturn(List.of());

        TicketMarginDto dto = service.computeMargin(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(0L, dto.nbCommandes());
        assertTrue(dto.marges().isEmpty());
    }
}
