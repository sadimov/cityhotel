package com.cityprojects.citybackend.controller.inventory;

import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.service.inventory.ServiceHotelierService;
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
 * REST API des services hoteliers (prestations facturables au client).
 *
 * <p>Lecture : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/RESTAURANT.
 * Ecriture : SUPERADMIN/ADMIN/GERANT.</p>
 */
@RestController
@RequestMapping("/api/inventory/services-hoteliers")
public class ServiceHotelierController {

    private final ServiceHotelierService service;

    public ServiceHotelierController(ServiceHotelierService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<ServiceHotelierDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<Page<ServiceHotelierDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            @RequestParam(value = "typeServiceId", required = false) Long typeServiceId,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(recherche, typeServiceId, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<List<ServiceHotelierDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ServiceHotelierDto> create(
            @Valid @RequestBody ServiceHotelierCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<ServiceHotelierDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ServiceHotelierCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
