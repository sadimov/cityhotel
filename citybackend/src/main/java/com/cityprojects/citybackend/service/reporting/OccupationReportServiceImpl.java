package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.OccupationDto.TypeChambreOccupation;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.projection.NuiteeOccupationProjection;
import com.cityprojects.citybackend.dto.reporting.projection.TypeChambreCountProjection;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation R-HEB-001 (Tour 40 MVP).
 *
 * <p>Read-only strict : aucune persistance. Tous les agregats viennent des
 * entites tenant {@code Chambre} / {@code Nuitee} / {@code TypeChambre} via
 * leurs repositories. Hibernate ajoute automatiquement {@code WHERE hotel_id = ?}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class OccupationReportServiceImpl implements OccupationReportService {

    private final ChambreRepository chambreRepository;
    private final NuiteeRepository nuiteeRepository;
    private final DocumentExportService documentExportService;

    public OccupationReportServiceImpl(ChambreRepository chambreRepository,
                                       NuiteeRepository nuiteeRepository,
                                       DocumentExportService documentExportService) {
        this.chambreRepository = chambreRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "occupation",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #periode + '-' + #from + '-' + #to + '-' + #reference")
    public OccupationDto computeOccupation(ReportPeriode periode, LocalDate from, LocalDate to, LocalDate reference) {
        ReportPeriode.DateRange range = resolveRange(periode, from, to, reference);
        long nbJours = ChronoUnit.DAYS.between(range.from(), range.to());

        long totalChambres = chambreRepository.countByActifTrue();
        long totalDispo = totalChambres * nbJours;
        long totalOccupees = nuiteeRepository.countOccupeesOnRange(range.from(), range.to());

        BigDecimal tauxGlobal = ratePercent(totalOccupees, totalDispo);

        List<TypeChambreCountProjection> counts = chambreRepository.countActivesGroupedByType();
        List<NuiteeOccupationProjection> occupees = nuiteeRepository
                .aggregateOccupationByType(range.from(), range.to());
        Map<Long, Long> occupByType = new HashMap<>();
        for (NuiteeOccupationProjection p : occupees) {
            occupByType.put(p.getTypeId(), p.getNbNuiteesOccupees() == null ? 0L : p.getNbNuiteesOccupees());
        }

        List<TypeChambreOccupation> breakdown = new ArrayList<>();
        for (TypeChambreCountProjection c : counts) {
            long nbChambres = c.getNbChambres() == null ? 0L : c.getNbChambres();
            long dispo = nbChambres * nbJours;
            long occ = occupByType.getOrDefault(c.getTypeId(), 0L);
            breakdown.add(new TypeChambreOccupation(
                    c.getTypeId(),
                    c.getTypeCode(),
                    c.getTypeNom(),
                    (int) nbChambres,
                    dispo,
                    occ,
                    ratePercent(occ, dispo)));
        }

        return new OccupationDto(
                range.from(),
                range.to(),
                (int) totalChambres,
                totalDispo,
                totalOccupees,
                tauxGlobal,
                breakdown);
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
        OccupationDto dto = computeOccupation(periode, from, to, reference);
        String title = "Rapport d'occupation";
        String period = String.format("Période : %s → %s (%s)", dto.from(), dto.to(), periode);

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Chambres actives", String.valueOf(dto.totalChambres())),
                new ReportDocument.Kpi("Nuitées disponibles", String.valueOf(dto.totalNuiteesDispo())),
                new ReportDocument.Kpi("Nuitées occupées", String.valueOf(dto.totalNuiteesOccupees())),
                new ReportDocument.Kpi("Taux global",
                        (dto.tauxOccupationGlobal() != null ? dto.tauxOccupationGlobal().toPlainString() : "0.00") + " %")
        );

        List<String> headers = List.of("Code", "Type", "Nb chambres", "Nuitées dispo.", "Nuitées occ.", "Taux (%)");
        List<List<Object>> rows = new ArrayList<>(dto.breakdownParType().size());
        for (TypeChambreOccupation row : dto.breakdownParType()) {
            rows.add(List.of(
                    row.typeCode() != null ? row.typeCode() : "",
                    row.typeNom() != null ? row.typeNom() : "",
                    row.nbChambres(),
                    row.nuiteesDispo(),
                    row.nuiteesOccupees(),
                    row.tauxOccupation() != null ? row.tauxOccupation().toPlainString() : "0.00"
            ));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Détail par type de chambre", headers, rows));
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

    private static BigDecimal ratePercent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
