package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.dto.admin.HotelAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelUpdateAdminDto;
import com.cityprojects.citybackend.service.admin.HotelAdminService;
import com.cityprojects.citybackend.util.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST de gestion des hotels par un SUPERADMIN.
 *
 * <p>Toutes les routes sont protegees par {@code hasRole('SUPERADMIN')} au
 * niveau classe. Aucune route metier non-admin ne doit etre ajoutee ici :
 * pour les actions courantes (consultation par un user de l'hotel courant),
 * passer par {@code /api/hotels/me} (a creer dans un futur tour profil).</p>
 *
 * <h2>Pas de DELETE physique</h2>
 * <p>{@code POST /{id}/desactiver} (soft delete) preserve l'historique
 * comptable et les FK aval. Reactivation toujours possible via
 * {@code POST /{id}/reactiver}.</p>
 */
@RestController
@RequestMapping("/api/admin/hotels")
@PreAuthorize("hasRole('SUPERADMIN')")
public class HotelAdminController {

    private final HotelAdminService hotelAdminService;

    public HotelAdminController(HotelAdminService hotelAdminService) {
        this.hotelAdminService = hotelAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<HotelAdminDto>>> findAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(hotelAdminService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelAdminDto>> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(hotelAdminService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HotelAdminDto>> create(
            @Valid @RequestBody HotelCreateAdminDto dto) {
        HotelAdminDto created = hotelAdminService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Hotel cree"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelAdminDto>> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody HotelUpdateAdminDto dto) {
        return ResponseEntity.ok(ApiResponse.success(hotelAdminService.update(id, dto)));
    }

    @PostMapping("/{id}/desactiver")
    public ResponseEntity<ApiResponse<Void>> desactiver(@PathVariable("id") Long id) {
        hotelAdminService.desactiver(id);
        return ResponseEntity.ok(ApiResponse.success("Hotel desactive"));
    }

    @PostMapping("/{id}/reactiver")
    public ResponseEntity<ApiResponse<Void>> reactiver(@PathVariable("id") Long id) {
        hotelAdminService.reactiver(id);
        return ResponseEntity.ok(ApiResponse.success("Hotel reactive"));
    }
}
