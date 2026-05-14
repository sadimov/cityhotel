package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.exception.BusinessException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Implementation R-DIR-001 — Dashboard direction (Tour 41 P2).
 *
 * <p>Orchestration des services existants : pas de duplication de logique.</p>
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

    public DashboardDirectionReportServiceImpl(OccupationReportService occupationService,
                                               CARecapReportService caRecapService,
                                               StockAlertReportService stockAlertService,
                                               RecapTachesReportService recapTachesService,
                                               KpiReceptionReportService kpiReceptionService) {
        this.occupationService = occupationService;
        this.caRecapService = caRecapService;
        this.stockAlertService = stockAlertService;
        this.recapTachesService = recapTachesService;
        this.kpiReceptionService = kpiReceptionService;
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
}
