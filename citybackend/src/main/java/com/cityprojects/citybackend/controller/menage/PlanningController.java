package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.dto.menage.PlanningCreateDto;
import com.cityprojects.citybackend.dto.menage.PlanningDto;
import com.cityprojects.citybackend.service.menage.PlanningService;
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
 * REST API du planning du personnel de menage (Tour 27).
 *
 * <p>Roles : ADMIN, GERANT (planification = responsabilite hierarchique).</p>
 */
@RestController
@RequestMapping("/api/menage/planning")
public class PlanningController {

    private final PlanningService service;

    public PlanningController(PlanningService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PlanningDto> create(@Valid @RequestBody PlanningCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{planningId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PlanningDto> update(@PathVariable("planningId") Long planningId,
                                              @Valid @RequestBody PlanningCreateDto dto) {
        return ResponseEntity.ok(service.update(planningId, dto));
    }

    @GetMapping("/{planningId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<PlanningDto> findById(@PathVariable("planningId") Long planningId) {
        return ResponseEntity.ok(service.findById(planningId));
    }

    @GetMapping("/personnel/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<List<PlanningDto>> findByPersonnel(
            @PathVariable("personnelId") Long personnelId,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findByPersonnel(personnelId, date != null ? date : LocalDate.now()));
    }

    @GetMapping("/date/{date}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<List<PlanningDto>> findByDate(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findByDate(date));
    }

    @GetMapping("/aujourd-hui")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<List<PlanningDto>> findAujourdhui() {
        return ResponseEntity.ok(service.findByDate(LocalDate.now()));
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<List<PlanningDto>> findDisponibles(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findDisponibles(date != null ? date : LocalDate.now()));
    }

    @DeleteMapping("/{planningId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> delete(@PathVariable("planningId") Long planningId) {
        service.delete(planningId);
        return ResponseEntity.noContent().build();
    }
}
