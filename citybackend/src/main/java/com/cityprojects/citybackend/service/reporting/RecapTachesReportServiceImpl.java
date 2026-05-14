package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.RecapBreakdownDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.TacheGroupBy;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
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
    private final XlsxExportService xlsxExportService;

    public RecapTachesReportServiceImpl(TacheRepository tacheRepository,
                                        XlsxExportService xlsxExportService) {
        this.tacheRepository = tacheRepository;
        this.xlsxExportService = xlsxExportService;
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
        RecapTacheDto dto = computeRecap(from, to, groupBy);
        List<ColumnSpec<RecapBreakdownDto>> columns = List.of(
                new ColumnSpec<>("Dimension", ColumnType.TEXT, RecapBreakdownDto::dimensionKey),
                new ColumnSpec<>("Nb taches", ColumnType.INTEGER, RecapBreakdownDto::nbTaches));
        return xlsxExportService.export("Recap_Taches", columns, dto.breakdown());
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
