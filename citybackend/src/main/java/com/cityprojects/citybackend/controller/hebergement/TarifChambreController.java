package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.dto.hebergement.MontantCalculDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreDto;
import com.cityprojects.citybackend.service.hebergement.TarifChambreService;
import jakarta.validation.Valid;
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
 * REST API des tarifs saisonniers de chambres (Tour 44 Phase 1).
 *
 * <p>Roles :</p>
 * <ul>
 *   <li>Lecture : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/NIGHTAUDIT.</li>
 *   <li>Calcul (utilise par le calendrier reservations) : meme matrice.</li>
 *   <li>CRUD ecriture : SUPERADMIN/ADMIN/GERANT (le tarif est sensible).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/hebergement/tarifs-chambre")
public class TarifChambreController {

    private final TarifChambreService service;

    public TarifChambreController(TarifChambreService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<TarifChambreDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/by-type/{typeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<TarifChambreDto>> findByType(@PathVariable("typeId") Long typeId) {
        return ResponseEntity.ok(service.findByType(typeId));
    }

    /**
     * Calcule le montant total d'un sejour pour un type de chambre.
     * Utilise par le calendrier (proposition de prix a la creation).
     */
    @GetMapping("/calculer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<MontantCalculDto> calculer(
            @RequestParam("typeChambreId") Long typeChambreId,
            @RequestParam("dateDebut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam("dateFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.calculer(typeChambreId, dateDebut, dateFin));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TarifChambreDto> create(@Valid @RequestBody TarifChambreCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TarifChambreDto> update(@PathVariable("id") Long id,
                                                   @Valid @RequestBody TarifChambreCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
