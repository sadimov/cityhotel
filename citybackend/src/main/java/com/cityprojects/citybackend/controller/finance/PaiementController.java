package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.service.finance.PaiementService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API des paiements.
 *
 * <p>Matrice rôles :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</li>
 *   <li>Creation/Affectation : SUPERADMIN, ADMIN, GERANT, RECEPTION.</li>
 *   <li>Annulation : SUPERADMIN, ADMIN.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/paiements")
public class PaiementController {

    private final PaiementService service;

    public PaiementController(PaiementService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<PaiementDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<PaiementDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<PaiementDto> create(@Valid @RequestBody PaiementCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/affecter")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<PaiementDto> affecter(@PathVariable("id") Long id,
                                                @Valid @RequestBody List<AffectationCreateDto> affectations) {
        return ResponseEntity.ok(service.affecter(id, affectations));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<PaiementDto> annuler(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.annuler(id));
    }
}
