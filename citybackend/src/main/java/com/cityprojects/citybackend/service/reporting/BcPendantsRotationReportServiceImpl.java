package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;
import com.cityprojects.citybackend.dto.reporting.projection.RotationProduitProjection;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.BonCommandeRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-INV-003 — BC pendants + rotation produits (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class BcPendantsRotationReportServiceImpl implements BcPendantsRotationReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final BonCommandeRepository bonCommandeRepository;
    private final MouvementStockRepository mouvementRepository;
    private final DocumentExportService documentExportService;

    public BcPendantsRotationReportServiceImpl(BonCommandeRepository bonCommandeRepository,
                                               MouvementStockRepository mouvementRepository,
                                               DocumentExportService documentExportService) {
        this.bonCommandeRepository = bonCommandeRepository;
        this.mouvementRepository = mouvementRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "bc-pendants",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get()")
    public List<BcPendantDto> findBcPendants() {
        List<BonCommande> pendants = bonCommandeRepository.findPendants();
        LocalDate today = LocalDate.now();
        List<BcPendantDto> result = new ArrayList<>(pendants.size());
        for (BonCommande bc : pendants) {
            int age = bc.getDateCommande() != null
                    ? (int) ChronoUnit.DAYS.between(bc.getDateCommande(), today)
                    : 0;
            result.add(new BcPendantDto(
                    bc.getBonCommandeId(),
                    bc.getNumeroBc(),
                    bc.getFournisseurId(),
                    bc.getStatut(),
                    bc.getDateCommande(),
                    bc.getDateLivraisonPrevue(),
                    age,
                    nz(bc.getMontantTotal())));
        }
        return result;
    }

    @Override
    @Cacheable(value = "rotation-produits",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to")
    public List<RotationProduitDto> computeRotation(LocalDate from, LocalDate to) {
        validate(from, to);

        Instant fromInstant = from.atStartOfDay(NOUAKCHOTT).toInstant();
        Instant toInstant = to.atStartOfDay(NOUAKCHOTT).toInstant();

        List<RotationProduitProjection> projections =
                mouvementRepository.aggregateRotation(fromInstant, toInstant);

        List<RotationProduitDto> result = new ArrayList<>(projections.size());
        for (RotationProduitProjection p : projections) {
            long sorties = nz(p.getTotalSorties());
            int stockActuel = p.getStockActuel() == null ? 0 : p.getStockActuel();
            // Stock moyen approxime = max(stockActuel, 1) pour eviter division par zero.
            BigDecimal stockMoyen = BigDecimal.valueOf(Math.max(stockActuel, 1));
            BigDecimal rotation = BigDecimal.valueOf(sorties)
                    .divide(stockMoyen, 2, RoundingMode.HALF_UP);
            result.add(new RotationProduitDto(
                    p.getProduitId(),
                    p.getCodeProduit(),
                    p.getNomProduit(),
                    sorties,
                    stockActuel,
                    rotation));
        }
        return result;
    }

    @Override
    public byte[] exportBcPendantsXlsx() { return documentExportService.toXlsx(buildBcPendantsDocument()); }
    @Override
    public byte[] exportBcPendantsDocx() { return documentExportService.toDocx(buildBcPendantsDocument()); }
    @Override
    public byte[] exportBcPendantsPdf() { return documentExportService.toPdf(buildBcPendantsDocument()); }

    @Override
    public byte[] exportRotationXlsx(LocalDate from, LocalDate to) { return documentExportService.toXlsx(buildRotationDocument(from, to)); }
    @Override
    public byte[] exportRotationDocx(LocalDate from, LocalDate to) { return documentExportService.toDocx(buildRotationDocument(from, to)); }
    @Override
    public byte[] exportRotationPdf(LocalDate from, LocalDate to) { return documentExportService.toPdf(buildRotationDocument(from, to)); }

    private ReportDocument buildBcPendantsDocument() {
        List<BcPendantDto> data = findBcPendants();
        String title = "Bons de commande pendants";
        String period = String.format("Date courante : %s · %d BC en attente", LocalDate.now(), data.size());

        BigDecimal totalMontant = data.stream()
                .map(d -> d.montantTotal() != null ? d.montantTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int ageMax = data.stream().mapToInt(d -> d.ageJours() != null ? d.ageJours() : 0).max().orElse(0);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb BC pendants", String.valueOf(data.size())),
                new ReportDocument.Kpi("Âge max (jours)", String.valueOf(ageMax)),
                new ReportDocument.Kpi("Montant total", money(totalMontant))
        );

        List<String> headers = List.of("Numéro", "Statut", "Date commande", "Livraison prévue",
                "Fournisseur", "Âge (j)", "Montant");
        List<List<Object>> rows = new ArrayList<>(data.size());
        for (BcPendantDto bc : data) {
            rows.add(List.of(
                    bc.numeroBc() != null ? bc.numeroBc() : "",
                    bc.statut() != null ? bc.statut().name() : "",
                    bc.dateCommande() != null ? bc.dateCommande().toString() : "",
                    bc.dateLivraisonPrevue() != null ? bc.dateLivraisonPrevue().toString() : "",
                    bc.fournisseurId() != null ? bc.fournisseurId() : "",
                    bc.ageJours() != null ? bc.ageJours() : 0,
                    money(bc.montantTotal())
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail BC pendants", headers, rows));
    }

    private ReportDocument buildRotationDocument(LocalDate from, LocalDate to) {
        List<RotationProduitDto> data = computeRotation(from, to);
        String title = "Rotation produits";
        String period = String.format("Période : %s → %s · %d produit(s)", from, to, data.size());

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb produits avec sortie", String.valueOf(data.size()))
        );

        List<String> headers = List.of("Code", "Produit", "Sorties", "Stock actuel", "Rotation");
        List<List<Object>> rows = new ArrayList<>(data.size());
        for (RotationProduitDto r : data) {
            rows.add(List.of(
                    r.codeProduit() != null ? r.codeProduit() : "",
                    r.nomProduit() != null ? r.nomProduit() : "",
                    r.totalSorties() != null ? r.totalSorties() : 0,
                    r.stockActuel() != null ? r.stockActuel() : 0,
                    r.rotation() != null ? r.rotation().toPlainString() : "0.00"
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Indice de rotation", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
    }
}
