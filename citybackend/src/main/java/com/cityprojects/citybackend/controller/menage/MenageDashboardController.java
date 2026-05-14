package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.dto.menage.DashboardMenageDto;
import com.cityprojects.citybackend.dto.menage.KpiMenageDto;
import com.cityprojects.citybackend.dto.menage.PerformancePersonnelDto;
import com.cityprojects.citybackend.dto.menage.StatistiquesMenageDto;
import com.cityprojects.citybackend.service.menage.MenageDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API du tableau de bord, des statistiques et des KPI du module
 * menage (sous-tour C — cf. {@code endpoints_module_menage.txt} §Dashboard).
 *
 * <h3>Roles (spec)</h3>
 * <ul>
 *   <li>Dashboard / statistiques generales / kpi : ADMIN, GERANT, RECEPTION.</li>
 *   <li>Performance personnel : ADMIN, GERANT (donnee RH plus sensible).</li>
 *   <li>SUPERADMIN inclus partout.</li>
 * </ul>
 *
 * <h3>Base path</h3>
 * <p>{@code /api/menage} (path racine) — distinct des sous-controllers
 * {@code /api/menage/personnel}, {@code /api/menage/taches}, etc.</p>
 */
@RestController
@RequestMapping("/api/menage")
public class MenageDashboardController {

    private final MenageDashboardService service;

    public MenageDashboardController(MenageDashboardService service) {
        this.service = service;
    }

    /**
     * Tableau de bord temps reel : stats du jour + top 5 taches en retard
     * + personnels disponibles aujourd'hui.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<DashboardMenageDto> getDashboard() {
        return ResponseEntity.ok(service.getDashboard());
    }

    /** Statistiques agregees pour la journee courante. */
    @GetMapping("/statistiques")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<StatistiquesMenageDto> getStatistiquesJour() {
        return ResponseEntity.ok(service.getStatistiquesJour());
    }

    /**
     * Statistiques agregees pour une periode arbitraire.
     *
     * @param dateDebut borne inferieure incluse (format ISO yyyy-MM-dd)
     * @param dateFin   borne superieure incluse (format ISO yyyy-MM-dd)
     */
    @GetMapping("/statistiques/periode")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<StatistiquesMenageDto> getStatistiquesPeriode(
            @RequestParam("dateDebut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam("dateFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getStatistiquesPeriode(dateDebut, dateFin));
    }

    /**
     * Statistiques de performance d'un personnel sur une periode.
     *
     * <p>Si {@code dateDebut}/{@code dateFin} sont absents, defaut =
     * 30 derniers jours (cf. {@code MenageDashboardServiceImpl#KPI_TREND_DAYS}).</p>
     */
    @GetMapping("/statistiques/personnel/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PerformancePersonnelDto> getPerformancePersonnel(
            @PathVariable("personnelId") Long personnelId,
            @RequestParam(value = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(value = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getPerformancePersonnel(personnelId, dateDebut, dateFin));
    }

    /** Indicateurs synthese vue tendance. */
    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<KpiMenageDto> getKpi() {
        return ResponseEntity.ok(service.getKpi());
    }
}
