package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.AnnulationCommandeDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.EncaissementCommandeDto;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.service.restaurant.CommandeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API des commandes POS restaurant (Tour 24).
 *
 * <h3>Roles</h3>
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC, RESTAURANT.</li>
 *   <li>Mutation (POST, encaissement, statut) : SUPERADMIN, ADMIN, GERANT, RESTAURANT.</li>
 *   <li>Annulation : idem mutation.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/restaurant/commandes")
public class CommandeController {

    private final CommandeService service;

    public CommandeController(CommandeService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<CommandeDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<Page<CommandeDto>> findByStatut(
            @RequestParam("statut") StatutCommande statut,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByStatut(statut, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CommandeDto> create(@Valid @RequestBody CommandeCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/statut/{nouveauStatut}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CommandeDto> changeStatut(
            @PathVariable("id") Long id,
            @PathVariable("nouveauStatut") StatutCommande nouveauStatut) {
        return ResponseEntity.ok(service.changeStatut(id, nouveauStatut));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CommandeDto> annuler(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnnulationCommandeDto dto) {
        return ResponseEntity.ok(service.annuler(id, dto.motif()));
    }

    @PostMapping("/{id}/encaisser")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CommandeDto> encaisser(
            @PathVariable("id") Long id,
            @Valid @RequestBody EncaissementCommandeDto dto) {
        return ResponseEntity.ok(service.encaisserComptant(id, dto));
    }
}
