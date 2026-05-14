package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;
import com.cityprojects.citybackend.dto.reporting.projection.RotationProduitProjection;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.BonCommandeRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
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
    private final XlsxExportService xlsxExportService;

    public BcPendantsRotationReportServiceImpl(BonCommandeRepository bonCommandeRepository,
                                               MouvementStockRepository mouvementRepository,
                                               XlsxExportService xlsxExportService) {
        this.bonCommandeRepository = bonCommandeRepository;
        this.mouvementRepository = mouvementRepository;
        this.xlsxExportService = xlsxExportService;
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
    public byte[] exportBcPendantsXlsx() {
        List<BcPendantDto> data = findBcPendants();
        List<ColumnSpec<BcPendantDto>> columns = List.of(
                new ColumnSpec<>("Numero", ColumnType.TEXT, BcPendantDto::numeroBc),
                new ColumnSpec<>("Statut", ColumnType.TEXT, bc -> bc.statut() == null ? "" : bc.statut().name()),
                new ColumnSpec<>("Date commande", ColumnType.DATE, BcPendantDto::dateCommande),
                new ColumnSpec<>("Livraison prevue", ColumnType.DATE, BcPendantDto::dateLivraisonPrevue),
                new ColumnSpec<>("Fournisseur", ColumnType.INTEGER, BcPendantDto::fournisseurId),
                new ColumnSpec<>("Age jours", ColumnType.INTEGER, BcPendantDto::ageJours),
                new ColumnSpec<>("Montant", ColumnType.MONEY, BcPendantDto::montantTotal));
        return xlsxExportService.export("BC_Pendants", columns, data);
    }

    @Override
    public byte[] exportRotationXlsx(LocalDate from, LocalDate to) {
        List<RotationProduitDto> data = computeRotation(from, to);
        List<ColumnSpec<RotationProduitDto>> columns = List.of(
                new ColumnSpec<>("Code", ColumnType.TEXT, RotationProduitDto::codeProduit),
                new ColumnSpec<>("Produit", ColumnType.TEXT, RotationProduitDto::nomProduit),
                new ColumnSpec<>("Sorties", ColumnType.INTEGER, RotationProduitDto::totalSorties),
                new ColumnSpec<>("Stock actuel", ColumnType.INTEGER, RotationProduitDto::stockActuel),
                new ColumnSpec<>("Rotation", ColumnType.DECIMAL, RotationProduitDto::rotation));
        return xlsxExportService.export("Rotation_Produits", columns, data);
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
