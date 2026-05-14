package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto.MouvementLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.MouvementValoriseProjection;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-INV-002 — Mouvements valorises (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class MouvementsValorisesReportServiceImpl implements MouvementsValorisesReportService {

    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final MouvementStockRepository mouvementRepository;
    private final XlsxExportService xlsxExportService;

    public MouvementsValorisesReportServiceImpl(MouvementStockRepository mouvementRepository,
                                                XlsxExportService xlsxExportService) {
        this.mouvementRepository = mouvementRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    @Cacheable(value = "mouvements-valorises",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #typeFilter")
    public MouvementValoriseDto computeMouvements(LocalDate from, LocalDate to, TypeMouvementStock typeFilter) {
        validate(from, to);

        Instant fromInstant = from.atStartOfDay(NOUAKCHOTT).toInstant();
        Instant toInstant = to.atStartOfDay(NOUAKCHOTT).toInstant();

        List<MouvementValoriseProjection> projections =
                mouvementRepository.findValorisesOnRange(fromInstant, toInstant, typeFilter);

        BigDecimal valeurEntrees = BigDecimal.ZERO;
        BigDecimal valeurSorties = BigDecimal.ZERO;
        List<MouvementLigneDto> lignes = new ArrayList<>(projections.size());

        for (MouvementValoriseProjection p : projections) {
            BigDecimal prix = p.getPrixUnitaireMouvement() != null
                    ? p.getPrixUnitaireMouvement()
                    : nz(p.getPrixUnitaireProduit());
            int qte = p.getQuantite() == null ? 0 : p.getQuantite();
            BigDecimal valeur = prix.multiply(BigDecimal.valueOf(qte));

            lignes.add(new MouvementLigneDto(
                    p.getMouvementId(),
                    p.getDate(),
                    p.getProduitId(),
                    p.getCodeProduit(),
                    p.getNomProduit(),
                    p.getTypeMouvement(),
                    qte,
                    prix,
                    valeur,
                    p.getReferenceDocument()));

            TypeMouvementStock type = p.getTypeMouvement();
            if (type == TypeMouvementStock.ENTREE) {
                valeurEntrees = valeurEntrees.add(valeur);
            } else if (type == TypeMouvementStock.SORTIE || type == TypeMouvementStock.PERTE) {
                valeurSorties = valeurSorties.add(valeur);
            }
        }

        return new MouvementValoriseDto(from, to, typeFilter,
                (long) lignes.size(), valeurEntrees, valeurSorties, lignes);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, TypeMouvementStock typeFilter) {
        MouvementValoriseDto dto = computeMouvements(from, to, typeFilter);
        List<ColumnSpec<MouvementLigneDto>> columns = List.of(
                new ColumnSpec<>("Date", ColumnType.DATETIME, MouvementLigneDto::date),
                new ColumnSpec<>("Code produit", ColumnType.TEXT, MouvementLigneDto::codeProduit),
                new ColumnSpec<>("Produit", ColumnType.TEXT, MouvementLigneDto::nomProduit),
                new ColumnSpec<>("Type", ColumnType.TEXT, l -> l.typeMouvement() == null ? "" : l.typeMouvement().name()),
                new ColumnSpec<>("Quantite", ColumnType.INTEGER, MouvementLigneDto::quantite),
                new ColumnSpec<>("Prix unitaire", ColumnType.MONEY, MouvementLigneDto::prixUnitaire),
                new ColumnSpec<>("Valeur", ColumnType.MONEY, MouvementLigneDto::valeur),
                new ColumnSpec<>("Reference", ColumnType.TEXT, MouvementLigneDto::referenceDocument));
        return xlsxExportService.export("Mouvements_Valorises", columns, dto.lignes());
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
