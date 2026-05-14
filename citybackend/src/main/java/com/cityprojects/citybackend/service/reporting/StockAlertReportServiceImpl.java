package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.StockAlertDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation R-INV-001 (Tour 40 MVP).
 *
 * <p>Reutilise {@code ProduitRepository#findEnAlerte()} qui filtre deja
 * {@code stockActuel <= seuilAlerte AND actif = true}. La classification
 * "CRITIQUE" vs "ALERTE" se fait cote service (regle metier).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class StockAlertReportServiceImpl implements StockAlertReportService {

    private final ProduitRepository produitRepository;
    private final XlsxExportService xlsxExportService;

    public StockAlertReportServiceImpl(ProduitRepository produitRepository,
                                       XlsxExportService xlsxExportService) {
        this.produitRepository = produitRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "stock-alerts",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get()")
    public List<StockAlertDto> listStockAlerts() {
        List<Produit> alertes = produitRepository.findEnAlerte();
        return alertes.stream().map(this::toDto).toList();
    }

    @Override
    public byte[] exportXlsx() {
        List<StockAlertDto> data = listStockAlerts();
        List<ColumnSpec<StockAlertDto>> columns = List.of(
                new ColumnSpec<>("Code", ColumnType.TEXT, StockAlertDto::codeProduit),
                new ColumnSpec<>("Produit", ColumnType.TEXT, StockAlertDto::nomProduit),
                new ColumnSpec<>("Unite", ColumnType.TEXT, StockAlertDto::uniteMesure),
                new ColumnSpec<>("Stock actuel", ColumnType.INTEGER, StockAlertDto::stockActuel),
                new ColumnSpec<>("Seuil alerte", ColumnType.INTEGER, StockAlertDto::seuilAlerte),
                new ColumnSpec<>("Seuil critique", ColumnType.INTEGER, StockAlertDto::seuilCritique),
                new ColumnSpec<>("Ecart", ColumnType.INTEGER, StockAlertDto::ecart),
                new ColumnSpec<>("Statut", ColumnType.TEXT, StockAlertDto::statut),
                new ColumnSpec<>("Valeur manquante (MRU)", ColumnType.MONEY, StockAlertDto::valeurManquante));
        return xlsxExportService.export("Alertes_Stock", columns, data);
    }

    private StockAlertDto toDto(Produit p) {
        int stock = p.getStockActuel() == null ? 0 : p.getStockActuel();
        int seuilAlerte = p.getSeuilAlerte() == null ? 0 : p.getSeuilAlerte();
        int seuilCritique = p.getSeuilCritique() == null ? 0 : p.getSeuilCritique();
        int ecart = Math.max(0, seuilAlerte - stock);
        String statut = stock <= seuilCritique ? "CRITIQUE" : "ALERTE";
        BigDecimal prix = p.getPrixUnitaire() == null ? BigDecimal.ZERO : p.getPrixUnitaire();
        BigDecimal valeurManquante = prix.multiply(BigDecimal.valueOf(ecart));
        return new StockAlertDto(
                p.getProduitId(),
                p.getCodeProduit(),
                p.getNomProduit(),
                p.getUniteMesure(),
                stock,
                seuilAlerte,
                seuilCritique,
                ecart,
                statut,
                valeurManquante);
    }
}
