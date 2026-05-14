package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.ExerciceDto;
import com.cityprojects.citybackend.service.finance.ExerciceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API des exercices comptables.
 *
 * <p>Matrice des rôles :</p>
 * <ul>
 *   <li>Lecture / current : {@code SUPERADMIN, ADMIN, GERANT}.</li>
 *   <li>Clôture (transition CLOTURE) : {@code SUPERADMIN, ADMIN} - opération
 *       sensible (irréversible).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/exercices")
public class ExerciceController {

    private final ExerciceService service;

    public ExerciceController(ExerciceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<ExerciceDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ExerciceDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    /** Retourne l'exercice contenant aujourd'hui, ou le cree (annee calendaire). */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ExerciceDto> findCurrent() {
        return ResponseEntity.ok(service.getOrCreateCurrent());
    }

    @PostMapping("/{id}/cloturer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ExerciceDto> cloturer(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.cloturer(id));
    }
}
