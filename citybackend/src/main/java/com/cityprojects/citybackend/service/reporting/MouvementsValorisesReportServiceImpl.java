package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto.MouvementLigneDto;
import com.cityprojects.citybackend.dto.reporting.projection.MouvementValoriseProjection;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
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
    private final DocumentExportService documentExportService;

    public MouvementsValorisesReportServiceImpl(MouvementStockRepository mouvementRepository,
                                                DocumentExportService documentExportService) {
        this.mouvementRepository = mouvementRepository;
        this.documentExportService = documentExportService;
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
        return documentExportService.toXlsx(buildDocument(from, to, typeFilter));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to, TypeMouvementStock typeFilter) {
        return documentExportService.toDocx(buildDocument(from, to, typeFilter));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to, TypeMouvementStock typeFilter) {
        return documentExportService.toPdf(buildDocument(from, to, typeFilter));
    }

    private ReportDocument buildDocument(LocalDate from, LocalDate to, TypeMouvementStock typeFilter) {
        MouvementValoriseDto dto = computeMouvements(from, to, typeFilter);
        String title = "Mouvements de stock valorisés";
        String period = String.format("Période : %s → %s · Type : %s",
                from, to, typeFilter != null ? typeFilter.name() : "TOUS");

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb mouvements", String.valueOf(dto.nbMouvements())),
                new ReportDocument.Kpi("Valeur entrées", money(dto.valeurEntrees())),
                new ReportDocument.Kpi("Valeur sorties", money(dto.valeurSorties())),
                new ReportDocument.Kpi("Solde", money(dto.valeurEntrees().subtract(dto.valeurSorties())))
        );

        List<String> headers = List.of("Date", "Code", "Produit", "Type",
                "Quantité", "PU", "Valeur", "Référence");
        List<List<Object>> rows = new ArrayList<>(dto.lignes().size());
        for (MouvementLigneDto l : dto.lignes()) {
            rows.add(List.of(
                    l.date() != null ? l.date().toString() : "",
                    l.codeProduit() != null ? l.codeProduit() : "",
                    l.nomProduit() != null ? l.nomProduit() : "",
                    l.typeMouvement() != null ? l.typeMouvement().name() : "",
                    l.quantite() != null ? l.quantite() : 0,
                    money(l.prixUnitaire()),
                    money(l.valeur()),
                    l.referenceDocument() != null ? l.referenceDocument() : ""
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail mouvements", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
