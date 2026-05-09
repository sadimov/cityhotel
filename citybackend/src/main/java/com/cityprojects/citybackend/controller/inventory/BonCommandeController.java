package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.BonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionBonCommandeDto;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import com.cityprojects.citybackend.service.inventory.BonCommandeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API des bons de commande fournisseur.
 *
 * <p>Workflow autorise :
 * <ul>
 *   <li>Creation/lecture/changement statut : SUPERADMIN, ADMIN, GERANT, MAGASIN.</li>
 *   <li>Reception (impact stock) : SUPERADMIN, ADMIN, GERANT, MAGASIN.</li>
 *   <li>Annulation : SUPERADMIN, ADMIN, GERANT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/inventory/bons-commande")
public class BonCommandeController {

    private final BonCommandeService service;

    public BonCommandeController(BonCommandeService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<BonCommandeDto>> findByStatut(
            @RequestParam(value = "statut", required = false) StatutBonCommande statut,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByStatut(statut, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> create(@Valid @RequestBody BonCommandeCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> changerStatut(@PathVariable("id") Long id,
                                                        @RequestParam("statut") StatutBonCommande statut) {
        return ResponseEntity.ok(service.changerStatut(id, statut));
    }

    @PostMapping("/{id}/reception")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> receptionner(@PathVariable("id") Long id,
                                                       @Valid @RequestBody ReceptionBonCommandeDto dto) {
        return ResponseEntity.ok(service.receptionner(id, dto));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<BonCommandeDto> annuler(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.annuler(id));
    }
}
