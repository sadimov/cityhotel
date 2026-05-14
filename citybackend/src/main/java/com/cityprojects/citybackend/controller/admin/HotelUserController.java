package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserResetPasswordResponseDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import com.cityprojects.citybackend.service.admin.HotelUserService;
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
 * Controller REST de gestion des utilisateurs par un ADMIN d'hotel
 * (perimetre du tenant courant).
 *
 * <h2>Decision de packaging</h2>
 * <p>Place dans {@code controller/admin/} (cote du DBUserAdminController
 * SUPERADMIN) pour rester thematiquement coherent — c'est une variante
 * "scope tenant" du meme domaine. La distinction se fait au niveau du
 * mapping {@code /api/hotel/users/**} et du {@code @PreAuthorize("hasRole('ADMIN')")}.</p>
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code GET /api/hotel/users} : liste paginee (tenant courant).</li>
 *   <li>{@code GET /api/hotel/users/{userId}} : detail (404 si cross-hotel).</li>
 *   <li>{@code POST /api/hotel/users} : creation (anti-escalation roles).</li>
 *   <li>{@code PUT /api/hotel/users/{userId}} : update.</li>
 *   <li>{@code POST /api/hotel/users/{userId}/verrouiller}</li>
 *   <li>{@code POST /api/hotel/users/{userId}/deverrouiller}</li>
 *   <li>{@code POST /api/hotel/users/{userId}/reset-password}</li>
 *   <li>{@code POST /api/hotel/users/{userId}/desactiver}</li>
 * </ul>
 *
 * <h2>Pas de hotelId en path</h2>
 * <p>Contrairement a {@link DBUserAdminController} (qui prend {@code /hotels/{hotelId}}
 * pour le SUPERADMIN cross-tenant), ici le {@code hotelId} est resolu
 * cote service via {@code TenantContext.get()} alimente par le JWT — IMPOSSIBLE
 * pour un ADMIN de viser un autre tenant via URL.</p>
 */
@RestController
@RequestMapping("/api/hotel/users")
@PreAuthorize("hasRole('ADMIN')")
public class HotelUserController {

    private final HotelUserService hotelUserService;

    public HotelUserController(HotelUserService hotelUserService) {
        this.hotelUserService = hotelUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DBUserAdminDto>>> findAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                hotelUserService.findAllInCurrentHotel(pageable)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<DBUserAdminDto>> findById(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(hotelUserService.findById(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DBUserAdminDto>> create(
            @Valid @RequestBody DBUserCreateAdminDto dto) {
        DBUserAdminDto created = hotelUserService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Utilisateur cree"));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<DBUserAdminDto>> update(
            @PathVariable Long userId,
            @Valid @RequestBody DBUserUpdateAdminDto dto) {
        return ResponseEntity.ok(ApiResponse.success(hotelUserService.update(userId, dto)));
    }

    @PostMapping("/{userId}/verrouiller")
    public ResponseEntity<ApiResponse<Void>> verrouiller(@PathVariable Long userId) {
        hotelUserService.verrouiller(userId);
        return ResponseEntity.ok(ApiResponse.success("Compte verrouille"));
    }

    @PostMapping("/{userId}/deverrouiller")
    public ResponseEntity<ApiResponse<Void>> deverrouiller(@PathVariable Long userId) {
        hotelUserService.deverrouiller(userId);
        return ResponseEntity.ok(ApiResponse.success("Compte deverrouille"));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<DBUserResetPasswordResponseDto>> resetPassword(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                hotelUserService.resetPassword(userId), "Mot de passe reinitialise"));
    }

    @PostMapping("/{userId}/desactiver")
    public ResponseEntity<ApiResponse<Void>> desactiver(@PathVariable Long userId) {
        hotelUserService.desactiver(userId);
        return ResponseEntity.ok(ApiResponse.success("Utilisateur desactive"));
    }
}
