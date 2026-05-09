package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.MouvementStockDto;
import com.cityprojects.citybackend.service.inventory.MouvementStockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API consultative des mouvements de stock (audit trail).
 */
@RestController
@RequestMapping("/api/inventory/mouvements")
public class MouvementStockController {

    private final MouvementStockService service;

    public MouvementStockController(MouvementStockService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<MouvementStockDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/produit/{produitId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<MouvementStockDto>> findByProduit(
            @PathVariable("produitId") Long produitId, Pageable pageable) {
        return ResponseEntity.ok(service.findByProduit(produitId, pageable));
    }
}
