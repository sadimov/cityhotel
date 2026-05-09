package com.cityprojects.citybackend.controller.client;

import com.cityprojects.citybackend.dto.client.SocieteCreateDto;
import com.cityprojects.citybackend.dto.client.SocieteDto;
import com.cityprojects.citybackend.dto.client.SocieteUpdateDto;
import com.cityprojects.citybackend.service.client.SocieteService;
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
 * REST API des societes (clients B2B / personnes morales).
 * <p>
 * Tous les endpoints operent dans le tenant de l'utilisateur authentifie ;
 * aucun {@code hotelId} dans les payloads (cf. CLAUDE.md racine §10).
 * <p>
 * Roles autorises (cf. CLAUDE.md racine §6.3) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</li>
 *   <li>Ecriture : SUPERADMIN, ADMIN, GERANT, RECEPTION.</li>
 *   <li>DELETE physique : SUPERADMIN, ADMIN.</li>
 * </ul>
 *
 * <h2>DELETE physique autorise (asymetrie volontaire avec {@link ClientController})</h2>
 * <p>Contrairement a {@link ClientController}, le DELETE physique est expose pour
 * les societes non utilisees (refus dans le service si des clients <b>actifs</b> y
 * sont rattaches). La FK {@code clients.societe_id} a {@code ON DELETE SET NULL}
 * (cf. changeset 003-3) pour preserver l'integrite si des clients <b>inactifs</b>
 * y sont encore rattaches : leur lien est detache plutot que de bloquer l'operation
 * ou casser l'integrite. Le soft delete (deactivate) reste l'option recommandee
 * pour conserver l'historique.</p>
 */
@RestController
@RequestMapping("/api/societes")
public class SocieteController {

    private final SocieteService societeService;

    public SocieteController(SocieteService societeService) {
        this.societeService = societeService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<SocieteDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(societeService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<SocieteDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            Pageable pageable) {
        return ResponseEntity.ok(societeService.search(recherche, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<List<SocieteDto>> findAllActive() {
        return ResponseEntity.ok(societeService.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<SocieteDto> create(@Valid @RequestBody SocieteCreateDto dto) {
        SocieteDto created = societeService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<SocieteDto> update(@PathVariable("id") Long id,
                                             @Valid @RequestBody SocieteUpdateDto dto) {
        return ResponseEntity.ok(societeService.update(id, dto));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        societeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        societeService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        societeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
