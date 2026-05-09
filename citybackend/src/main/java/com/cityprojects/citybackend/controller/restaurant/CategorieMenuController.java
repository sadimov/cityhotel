package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.service.restaurant.CategorieMenuService;
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
 * REST API du catalogue des categories de menu (par hotel).
 *
 * <p>Roles (Tour 23) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC, RESTAURANT
 *       (RESTAURANT inclus pour usage POS futur Tour 24+).</li>
 *   <li>Mutation (POST/PUT) : SUPERADMIN, ADMIN, GERANT, RESTAURANT.</li>
 *   <li>Suppression (deactivate) : SUPERADMIN, ADMIN, GERANT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/restaurant/categories")
public class CategorieMenuController {

    private final CategorieMenuService service;

    public CategorieMenuController(CategorieMenuService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<CategorieMenuDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<List<CategorieMenuDto>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','RESTAURANT')")
    public ResponseEntity<Page<CategorieMenuDto>> findAll(
            @RequestParam(value = "actif", required = false) Boolean actif,
            Pageable pageable) {
        return ResponseEntity.ok(service.findAll(actif, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CategorieMenuDto> create(@Valid @RequestBody CategorieMenuCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<CategorieMenuDto> update(@PathVariable("id") Long id,
                                                   @Valid @RequestBody CategorieMenuCreateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
