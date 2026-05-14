package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.DeclarationTvaCreateDto;
import com.cityprojects.citybackend.dto.finance.DeclarationTvaDto;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.service.finance.DeclarationTvaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API des déclarations TVA (B4).
 */
@RestController
@RequestMapping("/api/finance/tva/declarations")
public class DeclarationTvaController {

    private final DeclarationTvaService service;

    public DeclarationTvaController(DeclarationTvaService service) {
        this.service = service;
    }

    /** Calcule et crée (ou retourne) la déclaration BROUILLON pour une période. */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<DeclarationTvaDto> calculer(
            @Valid @RequestBody DeclarationTvaCreateDto body) {
        DeclarationTvaDto dto = service.calculer(body.dateDebut(), body.dateFin());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** Liste paginée des déclarations du tenant courant. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<DeclarationTvaDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    /** Lecture par id. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<DeclarationTvaDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    /** Recherche par période exacte. */
    @GetMapping("/periode")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<DeclarationTvaDto> findByPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return service.findByPeriode(dateDebut, dateFin)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("error.declaration.notFound"));
    }

    /** Validation : génère l'écriture de liquidation atomique. */
    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<DeclarationTvaDto> valider(@PathVariable Long id) {
        return ResponseEntity.ok(service.valider(id));
    }
}
