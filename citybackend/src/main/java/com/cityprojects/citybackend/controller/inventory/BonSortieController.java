package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.AnnulationBonSortieDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.entity.inventory.StatutBonSortie;
import com.cityprojects.citybackend.service.inventory.BonSortieService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

/**
 * REST API des bons de sortie de stock.
 */
@RestController
@RequestMapping("/api/inventory/bons-sortie")
public class BonSortieController {

    private final BonSortieService service;

    public BonSortieController(BonSortieService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonSortieDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<Page<BonSortieDto>> findByStatut(
            @RequestParam(value = "statut", required = false) StatutBonSortie statut,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByStatut(statut, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonSortieDto> create(@Valid @RequestBody BonSortieCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonSortieDto> valider(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.valider(id));
    }

    @PostMapping("/{id}/livrer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonSortieDto> livrer(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.livrer(id));
    }

    /**
     * Tour 51bis : annulation avec motif obligatoire.
     *
     * <p>Refus si le BS est deja {@code LIVRE} (faire un mouvement de
     * regularisation a la place).</p>
     */
    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<BonSortieDto> annuler(@PathVariable("id") Long id,
                                                @Valid @RequestBody AnnulationBonSortieDto dto) {
        return ResponseEntity.ok(service.annuler(id, dto.motif()));
    }
}
