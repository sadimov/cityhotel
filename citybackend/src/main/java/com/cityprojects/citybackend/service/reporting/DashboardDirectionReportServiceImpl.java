package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.service.reporting.export.DocumentExportService;
import com.cityprojects.citybackend.service.reporting.export.ReportDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation R-DIR-001 — Dashboard direction.
 *
 * <p>Orchestration des services existants : pas de duplication de logique.
 * Tour 51ter : exports unifiés via {@link DocumentExportService}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class DashboardDirectionReportServiceImpl implements DashboardDirectionReportService {

    private final OccupationReportService occupationService;
    private final CARecapReportService caRecapService;
    private final StockAlertReportService stockAlertService;
    private final RecapTachesReportService recapTachesService;
    private final KpiReceptionReportService kpiReceptionService;
    private final DocumentExportService documentExportService;

    public DashboardDirectionReportServiceImpl(OccupationReportService occupationService,
                                               CARecapReportService caRecapService,
                                               StockAlertReportService stockAlertService,
                                               RecapTachesReportService recapTachesService,
                                               KpiReceptionReportService kpiReceptionService,
                                               DocumentExportService documentExportService) {
        this.occupationService = occupationService;
        this.caRecapService = caRecapService;
        this.stockAlertService = stockAlertService;
        this.recapTachesService = recapTachesService;
        this.kpiReceptionService = kpiReceptionService;
        this.documentExportService = documentExportService;
    }

    @Override
    @Cacheable(value = "dashboard-direction",
            key = "T(com.cityprojects.citybackend.common.tenant.TenantContext).get() + '-' + #date")
    public DashboardDirectionDto computeDashboard(LocalDate date) {
        if (date == null) {
            throw new BusinessException("error.report.date.required");
        }

        OccupationDto occupation = occupationService.computeOccupation(
                ReportPeriode.JOUR, null, null, date);
        CARecapDto caJour = caRecapService.computeCA(
                ReportPeriode.JOUR, null, null, date);
        CARecapDto caSemaine = caRecapService.computeCA(
                ReportPeriode.SEMAINE, null, null, date);
        int nbAlertes = stockAlertService.listStockAlerts().size();
        long nbTachesEnCours = recapTachesService.countTachesEnCours();
        KpiReceptionDto kpi = kpiReceptionService.computeKpis(date);

        return new DashboardDirectionDto(
                date,
                occupation,
                caJour,
                caSemaine,
                nbAlertes,
                nbTachesEnCours,
                kpi.nbCheckIn(),
                kpi.nbCheckOut());
    }

    @Override
    public byte[] exportXlsx(LocalDate date) { return documentExportService.toXlsx(buildDocument(date)); }
    @Override
    public byte[] exportDocx(LocalDate date) { return documentExportService.toDocx(buildDocument(date)); }
    @Override
    public byte[] exportPdf(LocalDate date) { return documentExportService.toPdf(buildDocument(date)); }

    private ReportDocument buildDocument(LocalDate date) {
        DashboardDirectionDto dto = computeDashboard(date);
        String title = "Dashboard Direction";
        String period = String.format("Date : %s", dto.date());

        OccupationDto occ = dto.occupation();
        CARecapDto cj = dto.caJour();
        CARecapDto cs = dto.caSemaine();

        List<ReportDocument.Kpi> kpis = List.of(
                new ReportDocument.Kpi("Taux d'occupation",
                        occ != null && occ.tauxOccupationGlobal() != null
                                ? occ.tauxOccupationGlobal().toPlainString() + " %" : "0 %"),
                new ReportDocument.Kpi("CA du jour (TTC)", money(cj != null ? cj.caEmisTtc() : null)),
                new ReportDocument.Kpi("CA semaine (TTC)", money(cs != null ? cs.caEmisTtc() : null)),
                new ReportDocument.Kpi("Alertes stock", String.valueOf(dto.nbAlertesStock())),
                new ReportDocument.Kpi("Tâches en cours", String.valueOf(dto.nbTachesEnCours())),
                new ReportDocument.Kpi("Check-in du jour", String.valueOf(dto.nbCheckInJour())),
                new ReportDocument.Kpi("Check-out du jour", String.valueOf(dto.nbCheckOutJour()))
        );

        // Table récap : 1 ligne par indicateur pour conserver la cohérence visuelle
        List<String> headers = List.of("Indicateur", "Valeur");
        List<List<Object>> rows = new ArrayList<>(kpis.size());
        for (ReportDocument.Kpi k : kpis) {
            rows.add(List.of(k.label(), k.value()));
        }
        return new ReportDocument(title, period, kpis,
                new ReportDocument.Table("Synthèse direction", headers, rows));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0,00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
