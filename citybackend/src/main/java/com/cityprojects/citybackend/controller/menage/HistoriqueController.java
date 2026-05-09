package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import com.cityprojects.citybackend.service.menage.HistoriqueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API de l'historique des actions de menage (Tour 27).
 *
 * <p>Roles : ADMIN, GERANT, RECEPTION (consultation operationnelle).
 * Purge : ADMIN, GERANT uniquement.</p>
 */
@RestController
@RequestMapping("/api/menage/historique")
public class HistoriqueController {

    private final HistoriqueService service;

    public HistoriqueController(HistoriqueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<Page<HistoriqueDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/tache/{tacheId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<List<HistoriqueDto>> findByTache(@PathVariable("tacheId") Long tacheId) {
        return ResponseEntity.ok(service.findByTache(tacheId));
    }

    @GetMapping("/chambre/{chambreId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<List<HistoriqueDto>> findByChambre(@PathVariable("chambreId") Long chambreId) {
        return ResponseEntity.ok(service.findByChambre(chambreId));
    }

    @GetMapping("/personnel/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<List<HistoriqueDto>> findByPersonnel(@PathVariable("personnelId") Long personnelId) {
        return ResponseEntity.ok(service.findByPersonnel(personnelId));
    }

    @DeleteMapping("/nettoyer/{joursConservation}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Map<String, Integer>> nettoyer(
            @PathVariable("joursConservation") int joursConservation) {
        int n = service.nettoyer(joursConservation);
        return ResponseEntity.ok(Map.of("supprimees", n));
    }
}
