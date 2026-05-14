package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.ContrePassationDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.service.finance.EcritureComptableService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/**
 * REST API des ecritures comptables en partie double (B2).
 *
 * <p>Matrice des roles :</p>
 * <ul>
 *   <li>Lecture : {@code SUPERADMIN, ADMIN, GERANT}.</li>
 *   <li>Creation : {@code SUPERADMIN, ADMIN} - operation comptable
 *       sensible.</li>
 *   <li>Contre-passation : {@code SUPERADMIN, ADMIN} - operation comptable
 *       sensible (modifie l'historique).</li>
 * </ul>
 *
 * <p>Reponses : DTOs directs (pas d'enveloppe {@code ApiResponse}) pour
 * coherence avec {@code FactureController} et {@code PaiementController}.</p>
 */
@RestController
@RequestMapping("/api/finance/ecritures")
public class EcritureComptableController {

    private final EcritureComptableService service;

    public EcritureComptableController(EcritureComptableService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<EcritureComptableDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<EcritureComptableDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<EcritureComptableDto> create(@Valid @RequestBody EcritureComptableCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PostMapping("/{id}/contre-passer")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<EcritureComptableDto> contrePasser(@PathVariable("id") Long id,
                                                             @Valid @RequestBody ContrePassationDto dto) {
        return ResponseEntity.ok(service.contrePasser(id, dto.motif()));
    }

    @GetMapping("/journal/{journalId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<EcritureComptableDto>> findByJournal(
            @PathVariable("journalId") Long journalId,
            @RequestParam("dateDebut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam("dateFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByJournal(journalId, dateDebut, dateFin, pageable));
    }

    @GetMapping("/compte/{compteCode}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<EcritureComptableDto>> findByCompte(
            @PathVariable("compteCode") String compteCode,
            @RequestParam("dateDebut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam("dateFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByCompte(compteCode, dateDebut, dateFin, pageable));
    }

    @GetMapping("/exercice/{exerciceId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<EcritureComptableDto>> findByExercice(
            @PathVariable("exerciceId") Long exerciceId,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByExercice(exerciceId, pageable));
    }
}
