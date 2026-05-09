package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.service.inventory.CategorieProduitService;
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
 * REST API des categories de produits.
 */
@RestController
@RequestMapping("/api/inventory/categories")
public class CategorieProduitController {

    private final CategorieProduitService service;

    public CategorieProduitController(CategorieProduitService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<CategorieProduitDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<CategorieProduitDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<CategorieProduitDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<CategorieProduitDto> create(@Valid @RequestBody CategorieProduitCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<CategorieProduitDto> update(@PathVariable("id") Long id,
                                                      @Valid @RequestBody CategorieProduitCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
