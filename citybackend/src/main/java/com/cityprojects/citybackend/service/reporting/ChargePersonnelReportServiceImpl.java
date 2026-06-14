package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;
import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto.ChargeLigneDto;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-MEN-002 — Charge personnel (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ChargePersonnelReportServiceImpl implements ChargePersonnelReportService {

    private final TacheRepository tacheRepository;
    private final DocumentExportService documentExportService;

    public ChargePersonnelReportServiceImpl(TacheRepository tacheRepository,
                                            DocumentExportService documentExportService) {
        this.tacheRepository = tacheRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "charge-personnel",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to")
    public ChargePersonnelDto computeCharge(LocalDate from, LocalDate to) {
        validate(from, to);

        List<Tache> taches = tacheRepository.findOnRange(from, to);

        // Map<personnelId, [nbAssignees, nbTerminees, dureeTotalMin]>
        Map<Long, long[]> byPersonnel = new LinkedHashMap<>();
        for (Tache t : taches) {
            if (t.getPersonnelId() == null) {
                continue;
            }
            long[] agg = byPersonnel.computeIfAbsent(t.getPersonnelId(), k -> new long[]{0L, 0L, 0L});
            agg[0]++;
            if (t.getStatut() == StatutTache.TERMINEE) {
                agg[1]++;
            }
            if (t.getHeureDebutReelle() != null && t.getHeureFinReelle() != null
                    && t.getHeureFinReelle().isAfter(t.getHeureDebutReelle())) {
                long minutes = Duration.between(t.getHeureDebutReelle(), t.getHeureFinReelle())
                        .toMinutes();
                agg[2] += minutes;
            }
        }

        List<ChargeLigneDto> lignes = new ArrayList<>(byPersonnel.size());
        byPersonnel.forEach((personnelId, agg) -> {
            BigDecimal taux = agg[0] <= 0L
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(agg[1] * 100.0 / agg[0])
                            .setScale(2, RoundingMode.HALF_UP);
            lignes.add(new ChargeLigneDto(personnelId, agg[0], agg[1], agg[2], taux));
        });

        return new ChargePersonnelDto(from, to, lignes);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        return documentExportService.toXlsx(buildDocument(from, to));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to) {
        return documentExportService.toDocx(buildDocument(from, to));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to) {
        return documentExportService.toPdf(buildDocument(from, to));
    }

    private ReportDocument buildDocument(LocalDate from, LocalDate to) {
        ChargePersonnelDto dto = computeCharge(from, to);
        String title = "Charge personnel ménage";
        String period = String.format("Période : %s → %s", from, to);

        long totalAssignees = 0L;
        long totalTerminees = 0L;
        long totalDureeMin = 0L;
        for (ChargeLigneDto l : dto.personnels()) {
            totalAssignees += l.nbAssignees() != null ? l.nbAssignees() : 0L;
            totalTerminees += l.nbTerminees() != null ? l.nbTerminees() : 0L;
            totalDureeMin += l.dureeTotaleMin() != null ? l.dureeTotaleMin() : 0L;
        }

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Nb personnels", String.valueOf(dto.personnels().size())),
                new ReportDocument.Kpi("Total assignées", String.valueOf(totalAssignees)),
                new ReportDocument.Kpi("Total terminées", String.valueOf(totalTerminees)),
                new ReportDocument.Kpi("Durée totale (h)",
                        String.format("%.1f", totalDureeMin / 60.0))
        );

        List<String> headers = List.of("Personnel", "Assignées", "Terminées",
                "Durée totale (min)", "Taux completion %");
        List<List<Object>> rows = new ArrayList<>(dto.personnels().size());
        for (ChargeLigneDto l : dto.personnels()) {
            rows.add(List.of(
                    l.personnelId() != null ? l.personnelId() : "",
                    l.nbAssignees() != null ? l.nbAssignees() : 0,
                    l.nbTerminees() != null ? l.nbTerminees() : 0,
                    l.dureeTotaleMin() != null ? l.dureeTotaleMin() : 0,
                    l.tauxCompletion() != null ? l.tauxCompletion().toPlainString() : "0.00"
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail par personnel", headers, rows));
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
