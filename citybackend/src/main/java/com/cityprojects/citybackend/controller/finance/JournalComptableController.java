package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.JournalComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableUpdateDto;
import com.cityprojects.citybackend.service.finance.JournalComptableService;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * REST API des journaux comptables (B2).
 *
 * <p>Matrice des roles :</p>
 * <ul>
 *   <li>Lecture : {@code SUPERADMIN, ADMIN, GERANT}.</li>
 *   <li>Creation / modification / activation : {@code SUPERADMIN, ADMIN} -
 *       configuration sensible (impacte la numerotation des ecritures).</li>
 * </ul>
 *
 * <p>Reponses : DTOs directs (pas d'enveloppe {@code ApiResponse}) pour
 * coherence avec {@code FactureController} et {@code PaiementController}.</p>
 */
@RestController
@RequestMapping("/api/finance/journaux")
public class JournalComptableController {

    private final JournalComptableService service;

    public JournalComptableController(JournalComptableService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<List<JournalComptableDto>> findAll(
            @RequestParam(value = "actifsOnly", defaultValue = "false") boolean actifsOnly) {
        List<JournalComptableDto> result = actifsOnly ? service.findActifs() : service.findAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<JournalComptableDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<JournalComptableDto> create(@Valid @RequestBody JournalComptableCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<JournalComptableDto> update(@PathVariable("id") Long id,
                                                      @Valid @RequestBody JournalComptableUpdateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<JournalComptableDto> deactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.desactiver(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<JournalComptableDto> reactivate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.reactiver(id));
    }
}
