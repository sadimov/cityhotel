package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.dto.admin.ParametreAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreUpdateAdminDto;
import com.cityprojects.citybackend.service.admin.ParametreAdminService;
import com.cityprojects.citybackend.util.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST de gestion des parametres applicatifs globaux.
 *
 * <h2>Restriction "modifiable"</h2>
 * <p>L'update et le delete d'un parametre {@code modifiable=false} renvoient
 * 4xx avec code {@code error.parametre.notModifiable}. La creation force
 * toujours {@code modifiable=true} : pour rendre un parametre systeme
 * (modifiable=false), passer par un changeset Liquibase.</p>
 */
@RestController
@RequestMapping("/api/admin/parametres")
@PreAuthorize("hasRole('SUPERADMIN')")
public class ParametreAdminController {

    private final ParametreAdminService parametreAdminService;

    public ParametreAdminController(ParametreAdminService parametreAdminService) {
        this.parametreAdminService = parametreAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ParametreAdminDto>>> findAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(parametreAdminService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ParametreAdminDto>> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(parametreAdminService.findById(id)));
    }

    @GetMapping("/by-cle/{cle}")
    public ResponseEntity<ApiResponse<ParametreAdminDto>> findByCle(@PathVariable("cle") String cle) {
        return ResponseEntity.ok(ApiResponse.success(parametreAdminService.findByCle(cle)));
    }

    @GetMapping("/by-categorie/{categorie}")
    public ResponseEntity<ApiResponse<Page<ParametreAdminDto>>> findByCategorie(
            @PathVariable("categorie") String categorie, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                parametreAdminService.findByCategorie(categorie, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ParametreAdminDto>> create(
            @Valid @RequestBody ParametreCreateAdminDto dto) {
        ParametreAdminDto created = parametreAdminService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Parametre cree"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ParametreAdminDto>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ParametreUpdateAdminDto dto) {
        return ResponseEntity.ok(ApiResponse.success(parametreAdminService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        parametreAdminService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Parametre supprime"));
    }
}
