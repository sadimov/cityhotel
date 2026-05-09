package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.dto.hebergement.ChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.service.hebergement.ChambreService;
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
 * REST API des chambres physiques de l'hotel.
 *
 * <p>Lecture : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/MENAGE/NIGHTAUDIT.<br>
 * Mutation : SUPERADMIN/ADMIN/GERANT.<br>
 * Changement de statut : ouvert aussi a RECEPTION (check-in/out) et MENAGE
 * (transition NETTOYAGE -&gt; DISPONIBLE).</p>
 *
 * <p>Tour 14 audit B1 : prefixe migre de {@code /api/chambres} vers
 * {@code /api/hebergement/chambres}. B2 : ajout {@code reactivate} et
 * {@code disponibles}.</p>
 */
@RestController
@RequestMapping("/api/hebergement/chambres")
public class ChambreController {

    private final ChambreService chambreService;

    public ChambreController(ChambreService chambreService) {
        this.chambreService = chambreService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<ChambreDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(chambreService.findById(id));
    }

    @GetMapping("/by-numero/{numero}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<ChambreDto> findByNumero(@PathVariable("numero") String numero) {
        return ResponseEntity.ok(chambreService.findByNumero(numero));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<Page<ChambreDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(chambreService.findAll(pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<List<ChambreDto>> findAllActive() {
        return ResponseEntity.ok(chambreService.findAllActive());
    }

    @GetMapping("/by-type/{typeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<List<ChambreDto>> findByType(@PathVariable("typeId") Long typeId) {
        return ResponseEntity.ok(chambreService.findByType(typeId));
    }

    @GetMapping("/by-statut")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<List<ChambreDto>> findByStatut(@RequestParam("statut") StatutChambre statut) {
        return ResponseEntity.ok(chambreService.findByStatut(statut));
    }

    /**
     * Liste les chambres disponibles sur la periode {@code [dateDebut, dateFin)}
     * (Tour 14 B2 API). NIGHTAUDIT inclus pour consultation.
     */
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<List<ChambreDto>> findDisponibles(
            @RequestParam("dateDebut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam("dateFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(chambreService.findDisponibles(dateDebut, dateFin));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ChambreDto> create(@Valid @RequestBody ChambreCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chambreService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ChambreDto> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody ChambreCreateDto dto) {
        return ResponseEntity.ok(chambreService.update(id, dto));
    }

    @PostMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE')")
    public ResponseEntity<ChambreDto> changerStatut(@PathVariable("id") Long id,
                                                     @RequestParam("statut") StatutChambre statut) {
        return ResponseEntity.ok(chambreService.changerStatut(id, statut));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        chambreService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        chambreService.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
