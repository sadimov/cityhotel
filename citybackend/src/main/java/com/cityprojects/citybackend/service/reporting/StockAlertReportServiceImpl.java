package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.StockAlertDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-INV-001 — Alertes stock.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@link DocumentExportService} avec bordures partout.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class StockAlertReportServiceImpl implements StockAlertReportService {

    private final ProduitRepository produitRepository;
    private final DocumentExportService documentExportService;

    public StockAlertReportServiceImpl(ProduitRepository produitRepository,
                                       DocumentExportService documentExportService) {
        this.produitRepository = produitRepository;
        this.documentExportService = documentExportService;
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
        return documentExportService.toXlsx(buildDocument());
    }

    @Override
    public byte[] exportDocx() {
        return documentExportService.toDocx(buildDocument());
    }

    @Override
    public byte[] exportPdf() {
        return documentExportService.toPdf(buildDocument());
    }

    private ReportDocument buildDocument() {
        List<StockAlertDto> data = listStockAlerts();
        String title = "Alertes stock";
        String period = String.format("État courant — %d produit(s) sous seuil", data.size());

        long nbCritique = data.stream().filter(d -> "CRITIQUE".equals(d.statut())).count();
        BigDecimal valeurTotale = data.stream()
                .map(d -> d.valeurManquante() != null ? d.valeurManquante() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Produits en alerte", String.valueOf(data.size())),
                new ReportDocument.Kpi("Dont critique", String.valueOf(nbCritique)),
                new ReportDocument.Kpi("Valeur réappro. (MRU)", money(valeurTotale))
        );

        List<String> headers = List.of("Code", "Produit", "Unité",
                "Stock", "Seuil alerte", "Seuil critique", "Écart", "Statut", "Valeur manquante");
        List<List<Object>> rows = new ArrayList<>(data.size());
        for (StockAlertDto d : data) {
            rows.add(List.of(
                    d.codeProduit() != null ? d.codeProduit() : "",
                    d.nomProduit() != null ? d.nomProduit() : "",
                    d.uniteMesure() != null ? d.uniteMesure() : "",
                    d.stockActuel() != null ? d.stockActuel() : 0,
                    d.seuilAlerte() != null ? d.seuilAlerte() : 0,
                    d.seuilCritique() != null ? d.seuilCritique() : 0,
                    d.ecart() != null ? d.ecart() : 0,
                    d.statut() != null ? d.statut() : "",
                    money(d.valeurManquante())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail produits", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
