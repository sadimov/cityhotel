package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.RecapBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.TacheGroupBy;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-MEN-001 — Recap taches (Tour 41 P2).
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class RecapTachesReportServiceImpl implements RecapTachesReportService {

    private final TacheRepository tacheRepository;
    private final DocumentExportService documentExportService;

    public RecapTachesReportServiceImpl(TacheRepository tacheRepository,
                                        DocumentExportService documentExportService) {
        this.tacheRepository = tacheRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "recap-taches",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #from + '-' + #to + '-' + #groupBy")
    public RecapTacheDto computeRecap(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        validate(from, to, groupBy);

        List<Tache> taches = tacheRepository.findOnRange(from, to);
        Map<String, Long> agg = new LinkedHashMap<>();
        for (Tache t : taches) {
            String key = bucketKey(t, groupBy);
            agg.merge(key, 1L, Long::sum);
        }

        List<RecapBreakdownDto> breakdown = new ArrayList<>(agg.size());
        agg.forEach((k, v) -> breakdown.add(new RecapBreakdownDto(k, v)));

        return new RecapTacheDto(from, to, groupBy, (long) taches.size(), breakdown);
    }

    @Override
    public byte[] exportXlsx(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        return documentExportService.toXlsx(buildDocument(from, to, groupBy));
    }

    @Override
    public byte[] exportDocx(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        return documentExportService.toDocx(buildDocument(from, to, groupBy));
    }

    @Override
    public byte[] exportPdf(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        return documentExportService.toPdf(buildDocument(from, to, groupBy));
    }

    private ReportDocument buildDocument(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        RecapTacheDto dto = computeRecap(from, to, groupBy);
        String title = "Récap tâches ménage";
        String period = String.format("Période : %s → %s · Groupage : %s", from, to, groupBy);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Total tâches", String.valueOf(dto.totalTaches()))
        );

        List<String> headers = List.of("Dimension", "Nb tâches");
        List<List<Object>> rows = new ArrayList<>(dto.breakdown().size());
        for (RecapBreakdownDto b : dto.breakdown()) {
            rows.add(List.of(b.dimensionKey() != null ? b.dimensionKey() : "", b.nbTaches()));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail par " + groupBy, headers, rows));
    }

    @Override
    public long countTachesEnCours() {
        // Hibernate filtre auto par tenant via @TenantId sur Tache.
        return tacheRepository.findByStatutOrderByHeureDebutReelleAsc(StatutTache.EN_COURS).size();
    }

    private static String bucketKey(Tache t, TacheGroupBy groupBy) {
        return switch (groupBy) {
            case JOUR -> t.getDatePlanifiee() != null ? t.getDatePlanifiee().toString() : "INCONNU";
            case TYPE_TACHE -> t.getTypeNettoyage() != null ? t.getTypeNettoyage().name() : "INCONNU";
            case STATUT -> t.getStatut() != null ? t.getStatut().name() : "INCONNU";
        };
    }

    private static void validate(LocalDate from, LocalDate to, TacheGroupBy groupBy) {
        if (from == null || to == null) {
            throw new BusinessException("error.report.dateRange.required");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("error.report.dateRange.invalid");
        }
        if (groupBy == null) {
            throw new BusinessException("error.report.groupBy.required");
        }
    }
}
