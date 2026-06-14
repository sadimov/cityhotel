package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;
import com.cityprojects.citybackend.dto.reporting.EncoursClientDto.EncoursLigneDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-FIN-002 — Encours clients (Tour 41 P1).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class EncoursClientsReportServiceImpl implements EncoursClientsReportService {

    /** Buckets de vieillissement (jours). */
    static final int BUCKET_30 = 30;
    static final int BUCKET_60 = 60;
    static final int BUCKET_90 = 90;

    private final FactureRepository factureRepository;
    private final DocumentExportService documentExportService;

    public EncoursClientsReportServiceImpl(FactureRepository factureRepository,
                                           DocumentExportService documentExportService) {
        this.factureRepository = factureRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "encours-clients",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #reference")
    public EncoursClientDto computeEncours(LocalDate reference) {
        LocalDate ref = reference != null ? reference : LocalDate.now();

        List<Facture> factures = factureRepository.findFacturesNonSoldees();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal b30 = BigDecimal.ZERO;
        BigDecimal b60 = BigDecimal.ZERO;
        BigDecimal b90 = BigDecimal.ZERO;
        BigDecimal bPlus = BigDecimal.ZERO;

        List<EncoursLigneDto> lignes = new ArrayList<>(factures.size());
        for (Facture f : factures) {
            BigDecimal du = f.getMontantRestant();
            if (du == null || du.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int age = (int) ChronoUnit.DAYS.between(f.getDateFacture(), ref);
            if (age < 0) {
                age = 0;
            }
            String bucket;
            if (age <= BUCKET_30) {
                bucket = "0-30";
                b30 = b30.add(du);
            } else if (age <= BUCKET_60) {
                bucket = "30-60";
                b60 = b60.add(du);
            } else if (age <= BUCKET_90) {
                bucket = "60-90";
                b90 = b90.add(du);
            } else {
                bucket = "90+";
                bPlus = bPlus.add(du);
            }
            total = total.add(du);
            lignes.add(new EncoursLigneDto(
                    f.getFactureId(),
                    f.getNumeroFacture(),
                    f.getDateFacture(),
                    f.getDateEcheance(),
                    f.getClientId(),
                    nz(f.getMontantTtc()),
                    nz(f.getMontantPaye()),
                    du,
                    age,
                    bucket));
        }

        return new EncoursClientDto(ref, total, b30, b60, b90, bPlus, lignes);
    }

    @Override
    public byte[] exportXlsx(LocalDate reference) {
        return documentExportService.toXlsx(buildDocument(reference));
    }

    @Override
    public byte[] exportDocx(LocalDate reference) {
        return documentExportService.toDocx(buildDocument(reference));
    }

    @Override
    public byte[] exportPdf(LocalDate reference) {
        return documentExportService.toPdf(buildDocument(reference));
    }

    private ReportDocument buildDocument(LocalDate reference) {
        EncoursClientDto dto = computeEncours(reference);
        String title = "Encours clients";
        String period = String.format("Référence : %s", dto.reference());

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Total encours", money(dto.totalEncours())),
                new ReportDocument.Kpi("0 — 30 j", money(dto.bucket0_30())),
                new ReportDocument.Kpi("30 — 60 j", money(dto.bucket30_60())),
                new ReportDocument.Kpi("60 — 90 j", money(dto.bucket60_90())),
                new ReportDocument.Kpi("90+ j", money(dto.bucket90Plus()))
        );

        List<String> headers = List.of("Facture", "Date", "Échéance", "Client",
                "TTC", "Payé", "Dû", "Âge (j)", "Bucket");
        List<List<Object>> rows = new ArrayList<>(dto.lignes().size());
        for (EncoursLigneDto l : dto.lignes()) {
            rows.add(List.of(
                    l.numeroFacture() != null ? l.numeroFacture() : "",
                    l.dateFacture() != null ? l.dateFacture().toString() : "",
                    l.dateEcheance() != null ? l.dateEcheance().toString() : "",
                    l.clientId() != null ? l.clientId() : "",
                    money(l.montantTtc()),
                    money(l.montantPaye()),
                    money(l.montantDu()),
                    l.ageJours(),
                    l.bucket() != null ? l.bucket() : ""
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail factures non soldées", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
