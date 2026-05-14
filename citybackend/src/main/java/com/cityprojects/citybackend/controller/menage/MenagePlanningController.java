package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.service.menage.MenagePlanningService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API du planning ménage - Tour 30 (Workflow A).
 *
 * <p>Endpoint d'invocation manuelle de la generation du planning du jour. Le
 * scheduler {@link com.cityprojects.citybackend.service.menage.MenagePlanningScheduler}
 * fait deja tourner cette logique tous les jours a 12:05 Africa/Nouakchott
 * (cf. cron). Cet endpoint est utile pour rattraper apres incident ou pour
 * un declenchement manuel par un manager.</p>
 *
 * <h3>Path (sous-tour menage E4)</h3>
 * <p>Renomme {@code /api/menage/planning} -&gt; {@code /api/menage/planning-generation}
 * pour eviter la collision avec {@link PlanningController} qui sert le
 * CRUD des creneaux de planning. Endpoint hors spec
 * {@code endpoints_module_menage.txt} (admin/exploitation interne).</p>
 *
 * <h3>Roles autorises</h3>
 * <p>SUPERADMIN / ADMIN / GERANT / RECEPTION : la reception declenche
 * potentiellement des check-out manuels et peut vouloir forcer la generation.</p>
 */
@RestController
@RequestMapping("/api/menage/planning-generation")
public class MenagePlanningController {

    private final MenagePlanningService menagePlanningService;

    public MenagePlanningController(MenagePlanningService menagePlanningService) {
        this.menagePlanningService = menagePlanningService;
    }

    /**
     * Genere le planning ménage du jour. Si {@code date} est omise, prend
     * la date courante (timezone serveur Africa/Nouakchott).
     *
     * @return JSON {@code { "tachesCreees": N }}
     */
    @PostMapping("/generer-du-jour")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<Map<String, Integer>> genererDuJour(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate effective = (date != null) ? date : LocalDate.now();
        int created = menagePlanningService.genererPlanningDuJour(effective);
        return ResponseEntity.ok(Map.of("tachesCreees", created));
    }
}
