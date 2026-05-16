package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurDto;
import com.cityprojects.citybackend.service.inventory.FournisseurService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API des fournisseurs.
 *
 * <p>Matrice rôles inventory (CLAUDE.md racine §6.3) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, MAGASIN.</li>
 *   <li>Création/modification : SUPERADMIN, ADMIN, GERANT, MAGASIN.</li>
 *   <li>Suppression : SUPERADMIN, ADMIN.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/inventory/fournisseurs")
public class FournisseurController {

    private final FournisseurService service;

    public FournisseurController(FournisseurService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<FournisseurDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<FournisseurDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, pageable));
    }

    @GetMapping("/actifs")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<FournisseurDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<FournisseurDto> create(@Valid @RequestBody FournisseurCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<FournisseurDto> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody FournisseurCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
