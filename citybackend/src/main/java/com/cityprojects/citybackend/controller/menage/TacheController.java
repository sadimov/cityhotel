package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.dto.menage.AssignerTacheDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.dto.menage.TerminerTacheDto;
import com.cityprojects.citybackend.service.menage.TacheService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * REST API des taches de menage (Tour 27).
 *
 * <h3>Roles (cf. {@code MENAGE/endpoints_module_menage.txt})</h3>
 * <ul>
 *   <li>Lecture : ADMIN, GERANT, RECEPTION, MENAGE.</li>
 *   <li>Creation/modification/suppression : ADMIN, GERANT, MENAGE.</li>
 *   <li>Assignation : ADMIN, GERANT (responsabilite hierarchique).</li>
 *   <li>commencer / terminer : MENAGE en plus (operationnel terrain) +
 *       ADMIN/GERANT/RECEPTION.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/menage/taches")
public class TacheController {

    private final TacheService service;

    public TacheController(TacheService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<TacheDto> create(@Valid @RequestBody TacheCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{tacheId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<TacheDto> update(@PathVariable("tacheId") Long tacheId,
                                           @Valid @RequestBody TacheCreateDto dto) {
        return ResponseEntity.ok(service.update(tacheId, dto));
    }

    @GetMapping("/{tacheId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<TacheDto> findById(@PathVariable("tacheId") Long tacheId) {
        return ResponseEntity.ok(service.findById(tacheId));
    }

    @GetMapping("/date/{date}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findByDate(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findByDate(date));
    }

    @GetMapping("/aujourd-hui")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findAujourdhui() {
        return ResponseEntity.ok(service.findByDate(LocalDate.now()));
    }

    @GetMapping("/personnel/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findByPersonnel(
            @PathVariable("personnelId") Long personnelId,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(service.findByPersonnel(personnelId, effectiveDate));
    }

    @GetMapping("/en-cours")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findEnCours() {
        return ResponseEntity.ok(service.findEnCours());
    }

    @GetMapping("/en-retard")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findEnRetard() {
        return ResponseEntity.ok(service.findEnRetard());
    }

    @GetMapping("/non-assignees")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<List<TacheDto>> findNonAssignees(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findNonAssignees(date != null ? date : LocalDate.now()));
    }

    @PutMapping("/{tacheId}/assigner")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TacheDto> assigner(@PathVariable("tacheId") Long tacheId,
                                             @Valid @RequestBody AssignerTacheDto dto) {
        return ResponseEntity.ok(service.assigner(tacheId, dto));
    }

    @PutMapping("/{tacheId}/commencer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<TacheDto> commencer(@PathVariable("tacheId") Long tacheId) {
        return ResponseEntity.ok(service.commencer(tacheId));
    }

    @PutMapping("/{tacheId}/terminer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<TacheDto> terminer(@PathVariable("tacheId") Long tacheId,
                                             @Valid @RequestBody(required = false) TerminerTacheDto dto) {
        return ResponseEntity.ok(service.terminer(tacheId, dto));
    }

    /**
     * Tour 30 etape 8 : annulation d'une tache (alternative au DELETE).
     * Refusee si la tache est deja TERMINEE ou ANNULEE.
     */
    @PostMapping("/{tacheId}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<TacheDto> annuler(@PathVariable("tacheId") Long tacheId,
                                            @RequestBody(required = false) AnnulerTacheDto dto) {
        String motif = (dto != null) ? dto.motif() : null;
        return ResponseEntity.ok(service.annuler(tacheId, motif));
    }

    /**
     * Body record interne pour l'endpoint {@code POST /annuler}. Defini ici
     * (et non dans {@code dto/menage/}) car uniquement consomme par cet endpoint
     * et tres simple ; si d'autres champs metier s'ajoutent, le promouvoir en
     * fichier propre.
     */
    public record AnnulerTacheDto(String motif) {
    }

    @GetMapping("/rechercher")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','MENAGE')")
    public ResponseEntity<Page<TacheDto>> search(@RequestParam("terme") String terme, Pageable pageable) {
        return ResponseEntity.ok(service.search(terme, pageable));
    }

    @DeleteMapping("/{tacheId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> delete(@PathVariable("tacheId") Long tacheId) {
        service.delete(tacheId);
        return ResponseEntity.noContent().build();
    }
}
