package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.projection.CARecapProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation R-FIN-001 — Récap chiffre d'affaires.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@link DocumentExportService} avec bordures sur toutes les tables.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CARecapReportServiceImpl implements CARecapReportService {

    private static final String DEVISE = "MRU";

    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;
    private final DocumentExportService documentExportService;

    public CARecapReportServiceImpl(FactureRepository factureRepository,
                                    PaiementRepository paiementRepository,
                                    DocumentExportService documentExportService) {
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "ca-recap",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #periode + '-' + #from + '-' + #to + '-' + #reference")
    public CARecapDto computeCA(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        ReportPeriode.DateRange range = resolveRange(periode, from, to, reference);

        CARecapProjection agg = factureRepository.aggregateCaOnRange(range.from(), range.to());
        long nbPaiements = paiementRepository.countValidesOnRange(range.from(), range.to());
        BigDecimal montantEncaisse = paiementRepository.sumMontantValidesOnRange(range.from(), range.to());

        long nbFactures = (agg == null || agg.getNbFactures() == null) ? 0L : agg.getNbFactures();
        BigDecimal ht = nz(agg == null ? null : agg.getCaEmisHt());
        BigDecimal tva = nz(agg == null ? null : agg.getCaEmisTva());
        BigDecimal ttc = nz(agg == null ? null : agg.getCaEmisTtc());
        BigDecimal payeTtc = nz(agg == null ? null : agg.getCaPayeTtc());

        return new CARecapDto(
                range.from(), range.to(),
                nbFactures, ht, tva, ttc, payeTtc,
                nbPaiements, nz(montantEncaisse), DEVISE);
    }

    @Override
    public byte[] exportXlsx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        return documentExportService.toXlsx(buildDocument(periode, from, to, reference));
    }

    @Override
    public byte[] exportDocx(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        return documentExportService.toDocx(buildDocument(periode, from, to, reference));
    }

    @Override
    public byte[] exportPdf(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        return documentExportService.toPdf(buildDocument(periode, from, to, reference));
    }

    private ReportDocument buildDocument(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        CARecapDto dto = computeCA(periode, from, to, reference);
        String title = "Récapitulatif Chiffre d'Affaires";
        String period = String.format("Période : %s → %s · Devise : %s", dto.from(), dto.to(), dto.devise());

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb factures émises", String.valueOf(dto.nbFactures())),
                new ReportDocument.Kpi("CA HT", money(dto.caEmisHt())),
                new ReportDocument.Kpi("CA TVA", money(dto.caEmisTva())),
                new ReportDocument.Kpi("CA TTC", money(dto.caEmisTtc())),
                new ReportDocument.Kpi("Déjà payé TTC", money(dto.caPayeTtc())),
                new ReportDocument.Kpi("Nb paiements validés", String.valueOf(dto.nbPaiements())),
                new ReportDocument.Kpi("Encaissé", money(dto.montantEncaisse()))
        );

        List<String> headers = List.of("Indicateur", "Valeur");
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (ReportDocument.Kpi k : kpis) {
            rows.add(List.of(k.label(), k.value()));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Récapitulatif", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static ReportPeriode.DateRange resolveRange(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        if (periode == null) {
            throw new BusinessException("error.report.periode.missing");
        }
        if (periode == ReportPeriode.CUSTOM) {
            if (from == null || to == null) {
                throw new BusinessException("error.report.dateRange.required");
            }
            if (!from.isBefore(to)) {
                throw new BusinessException("error.report.dateRange.invalid");
            }
            return new ReportPeriode.DateRange(from, to);
        }
        LocalDate ref = reference != null ? reference : LocalDate.now();
        return periode.resolve(ref);
    }
}
