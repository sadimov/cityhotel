package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;
import com.cityprojects.citybackend.dto.reporting.EncoursClientDto.EncoursLigneDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final XlsxExportService xlsxExportService;

    public EncoursClientsReportServiceImpl(FactureRepository factureRepository,
                                           XlsxExportService xlsxExportService) {
        this.factureRepository = factureRepository;
        this.xlsxExportService = xlsxExportService;
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
        EncoursClientDto dto = computeEncours(reference);
        List<ColumnSpec<EncoursLigneDto>> columns = List.of(
                new ColumnSpec<>("Facture", ColumnType.TEXT, EncoursLigneDto::numeroFacture),
                new ColumnSpec<>("Date", ColumnType.DATE, EncoursLigneDto::dateFacture),
                new ColumnSpec<>("Echeance", ColumnType.DATE, EncoursLigneDto::dateEcheance),
                new ColumnSpec<>("Client", ColumnType.INTEGER, EncoursLigneDto::clientId),
                new ColumnSpec<>("Montant TTC", ColumnType.MONEY, EncoursLigneDto::montantTtc),
                new ColumnSpec<>("Deja paye", ColumnType.MONEY, EncoursLigneDto::montantPaye),
                new ColumnSpec<>("Du", ColumnType.MONEY, EncoursLigneDto::montantDu),
                new ColumnSpec<>("Age jours", ColumnType.INTEGER, EncoursLigneDto::ageJours),
                new ColumnSpec<>("Bucket", ColumnType.TEXT, EncoursLigneDto::bucket));
        return xlsxExportService.export("Encours_Clients", columns, dto.lignes());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
