package com.cityprojects.citybackend.controller.menage;

import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.service.menage.PersonnelService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API du personnel de menage (Tour 27).
 *
 * <h3>Roles (cf. {@code MENAGE/endpoints_module_menage.txt})</h3>
 * <ul>
 *   <li>CRUD Personnel : ADMIN, GERANT (RH).</li>
 *   <li>Lecture etendue : MENAGE en plus pour la consultation operationnelle.</li>
 *   <li>SUPERADMIN inclus dans toutes les routes.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/menage/personnel")
public class PersonnelController {

    private final PersonnelService service;

    public PersonnelController(PersonnelService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PersonnelDto> create(@Valid @RequestBody PersonnelCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<PersonnelDto> update(@PathVariable("personnelId") Long personnelId,
                                               @Valid @RequestBody PersonnelCreateDto dto) {
        return ResponseEntity.ok(service.update(personnelId, dto));
    }

    @GetMapping("/{personnelId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<PersonnelDto> findById(@PathVariable("personnelId") Long personnelId) {
        return ResponseEntity.ok(service.findById(personnelId));
    }

    @GetMapping("/numero/{numeroEmploye}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<PersonnelDto> findByNumero(@PathVariable("numeroEmploye") String numeroEmploye) {
        return ResponseEntity.ok(service.findByNumero(numeroEmploye));
    }

    @GetMapping("/actifs")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE','RECEPTION')")
    public ResponseEntity<List<PersonnelDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Page<PersonnelDto>> findAll(
            @RequestParam(value = "actif", required = false) Boolean actif,
            Pageable pageable) {
        return ResponseEntity.ok(service.findAll(actif, pageable));
    }

    @GetMapping("/rechercher")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<List<PersonnelDto>> search(@RequestParam("terme") String terme) {
        return ResponseEntity.ok(service.search(terme));
    }

    @GetMapping("/specialite/{specialite}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<List<PersonnelDto>> findBySpecialite(@PathVariable("specialite") String specialite) {
        return ResponseEntity.ok(service.findBySpecialite(specialite));
    }

    @PutMapping("/{personnelId}/desactiver")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("personnelId") Long personnelId) {
        service.deactivate(personnelId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{personnelId}/reactiver")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("personnelId") Long personnelId) {
        service.reactivate(personnelId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Personnel actif ET planifie disponible pour une date donnee
     * (sous-tour menage B1, cf. {@code endpoints_module_menage.txt} ligne 41).
     *
     * <p>Param {@code date} : optionnel, format ISO {@code yyyy-MM-dd}.
     * Si absent, defaut = aujourd'hui (TZ serveur Africa/Nouakchott via
     * {@link java.time.Clock} injecte).</p>
     *
     * <p>Reponse : liste des {@link PersonnelDto} (sans pagination — la
     * cardinalite typique est faible, < 50 agents par hotel).</p>
     */
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')")
    public ResponseEntity<List<PersonnelDto>> findDisponibles(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findDisponibles(date));
    }
}
