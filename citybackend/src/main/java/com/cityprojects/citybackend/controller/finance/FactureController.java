package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.service.finance.FactureService;
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

/**
 * REST API des factures.
 *
 * <p>Matrice rôles (alignée prompt Tour 19) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</li>
 *   <li>Creation : SUPERADMIN, ADMIN, GERANT, RECEPTION.</li>
 *   <li>Validation/Emission : SUPERADMIN, ADMIN, GERANT.</li>
 *   <li>Annulation (soft delete) : SUPERADMIN, ADMIN.</li>
 *   <li>fromReservation : SUPERADMIN, ADMIN, GERANT, RECEPTION (workflow check-out).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/factures")
public class FactureController {

    private final FactureService service;

    public FactureController(FactureService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<FactureDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<FactureDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<FactureDto> create(@Valid @RequestBody FactureCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/emettre")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<FactureDto> emettre(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.emettre(id));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<FactureDto> annuler(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.annuler(id));
    }

    @PostMapping("/from-reservation/{reservationId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<FactureDto> fromReservation(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.fromReservation(reservationId));
    }
}
