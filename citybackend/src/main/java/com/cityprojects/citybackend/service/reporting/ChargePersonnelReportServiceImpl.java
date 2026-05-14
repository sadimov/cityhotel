package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;
import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto.ChargeLigneDto;
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
    private final XlsxExportService xlsxExportService;

    public ChargePersonnelReportServiceImpl(TacheRepository tacheRepository,
                                            XlsxExportService xlsxExportService) {
        this.tacheRepository = tacheRepository;
        this.xlsxExportService = xlsxExportService;
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
        ChargePersonnelDto dto = computeCharge(from, to);
        List<ColumnSpec<ChargeLigneDto>> columns = List.of(
                new ColumnSpec<>("Personnel", ColumnType.INTEGER, ChargeLigneDto::personnelId),
                new ColumnSpec<>("Assignees", ColumnType.INTEGER, ChargeLigneDto::nbAssignees),
                new ColumnSpec<>("Terminees", ColumnType.INTEGER, ChargeLigneDto::nbTerminees),
                new ColumnSpec<>("Duree totale (min)", ColumnType.INTEGER, ChargeLigneDto::dureeTotaleMin),
                new ColumnSpec<>("Taux completion %", ColumnType.DECIMAL, ChargeLigneDto::tauxCompletion));
        return xlsxExportService.export("Charge_Personnel", columns, dto.personnels());
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
