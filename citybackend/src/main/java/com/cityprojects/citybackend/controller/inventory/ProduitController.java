package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.AjustementStockDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.service.inventory.ProduitService;
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
 * REST API des produits (catalogue + stock).
 */
@RestController
@RequestMapping("/api/inventory/produits")
public class ProduitController {

    private final ProduitService service;

    public ProduitController(ProduitService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<ProduitDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<ProduitDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            @RequestParam(value = "categorieId", required = false) Long categorieId,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, categorieId, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<ProduitDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping("/alertes")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<ProduitDto>> findEnAlerte() {
        return ResponseEntity.ok(service.findEnAlerte());
    }

    @GetMapping("/critiques")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<ProduitDto>> findEnStockCritique() {
        return ResponseEntity.ok(service.findEnStockCritique());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<ProduitDto> create(@Valid @RequestBody ProduitCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<ProduitDto> update(@PathVariable("id") Long id,
                                             @Valid @RequestBody ProduitCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/ajuster-stock")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ProduitDto> ajusterStock(@PathVariable("id") Long id,
                                                   @Valid @RequestBody AjustementStockDto dto) {
        return ResponseEntity.ok(service.ajusterStock(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
