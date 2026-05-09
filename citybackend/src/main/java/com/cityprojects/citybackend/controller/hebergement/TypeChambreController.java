package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.dto.hebergement.TypeChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreDto;
import com.cityprojects.citybackend.service.hebergement.TypeChambreService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * REST API du catalogue des types de chambres (par hotel).
 *
 * <p>Roles : la lecture est ouverte aux operationnels (RESREC, RECEPTION,
 * MENAGE, NIGHTAUDIT) ; la mutation est reservee aux gerants/admins.</p>
 */
@RestController
@RequestMapping("/api/hebergement/types-chambres")
public class TypeChambreController {

    private final TypeChambreService typeChambreService;

    public TypeChambreController(TypeChambreService typeChambreService) {
        this.typeChambreService = typeChambreService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<TypeChambreDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(typeChambreService.findById(id));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<List<TypeChambreDto>> findAllActive() {
        return ResponseEntity.ok(typeChambreService.findAllActive());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','MENAGE','NIGHTAUDIT')")
    public ResponseEntity<Page<TypeChambreDto>> findAll(
            @RequestParam(value = "actif", required = false) Boolean actif,
            Pageable pageable) {
        return ResponseEntity.ok(typeChambreService.findAll(actif, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TypeChambreDto> create(@Valid @RequestBody TypeChambreCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(typeChambreService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TypeChambreDto> update(@PathVariable("id") Long id,
                                                  @Valid @RequestBody TypeChambreCreateDto dto) {
        return ResponseEntity.ok(typeChambreService.update(id, dto));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        typeChambreService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        typeChambreService.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
