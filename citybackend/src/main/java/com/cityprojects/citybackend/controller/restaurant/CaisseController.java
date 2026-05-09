package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ClotureCaisseDto;
import com.cityprojects.citybackend.service.restaurant.CaisseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API de la caisse restaurant : cloture journaliere ("Z de caisse",
 * Tour 26.1).
 *
 * <h3>Roles</h3>
 * <ul>
 *   <li>SUPERADMIN, ADMIN, GERANT : tableau de bord financier.</li>
 *   <li>RESTAURANT : ecran serveur en fin de service.</li>
 * </ul>
 *
 * <h3>Decision arbitree (3=i)</h3>
 * <p>Endpoint en LECTURE SEULE : agregation a la demande, AUCUNE persistance.
 * Pour une historisation, prevoir une table {@code restaurant.clotures_caisse}
 * + service dedie dans un tour ulterieur.</p>
 */
@RestController
@RequestMapping("/api/restaurant/caisse")
public class CaisseController {

    private final CaisseService caisseService;

    public CaisseController(CaisseService caisseService) {
        this.caisseService = caisseService;
    }

    /**
     * GET /api/restaurant/caisse/cloture?date=YYYY-MM-DD
     *
     * <p>Retourne l'agregat des paiements VALIDES + compteurs commandes pour
     * le jour {@code date} dans le tenant courant. Hibernate filtre auto par
     * hotel via {@code @TenantId}.</p>
     */
    @GetMapping("/cloture")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<ClotureCaisseDto> cloture(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(caisseService.statsJournalieres(date));
    }
}
