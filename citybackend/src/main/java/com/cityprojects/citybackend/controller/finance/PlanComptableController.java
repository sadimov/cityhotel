package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.PlanComptableGeneralDto;
import com.cityprojects.citybackend.service.finance.PlanComptableService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API du Plan Comptable Général mauritanien (lecture seule).
 *
 * <p>Le PCG est un référentiel global immuable côté application. Les
 * évolutions passent par des migrations Liquibase. Aucun endpoint
 * d'écriture (POST/PUT/DELETE).</p>
 *
 * <p>Accès réservé aux profils administratifs / gestion :
 * {@code SUPERADMIN, ADMIN, GERANT}.</p>
 */
@RestController
@RequestMapping("/api/finance/plan-comptable")
public class PlanComptableController {

    private final PlanComptableService service;

    public PlanComptableController(PlanComptableService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<PlanComptableGeneralDto>> findAll(
            @RequestParam(name = "utilisableOnly", required = false, defaultValue = "false")
            boolean utilisableOnly,
            Pageable pageable) {
        return ResponseEntity.ok(service.findAll(utilisableOnly, pageable));
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PlanComptableGeneralDto> findByCode(@PathVariable("code") String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
