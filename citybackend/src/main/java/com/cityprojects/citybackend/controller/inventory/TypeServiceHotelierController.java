package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import com.cityprojects.citybackend.service.inventory.TypeServiceHotelierService;
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
 * REST API des types de services hoteliers.
 *
 * <p>Lecture : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/RESTAURANT.
 * Ecriture : SUPERADMIN/ADMIN/GERANT.</p>
 */
@RestController
@RequestMapping("/api/inventory/types-services-hoteliers")
public class TypeServiceHotelierController {

    private final TypeServiceHotelierService service;

    public TypeServiceHotelierController(TypeServiceHotelierService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<TypeServiceHotelierDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<Page<TypeServiceHotelierDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, pageable));
    }

    @GetMapping({"/active", "/actifs"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<List<TypeServiceHotelierDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TypeServiceHotelierDto> create(
            @Valid @RequestBody TypeServiceHotelierCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TypeServiceHotelierDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody TypeServiceHotelierCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
