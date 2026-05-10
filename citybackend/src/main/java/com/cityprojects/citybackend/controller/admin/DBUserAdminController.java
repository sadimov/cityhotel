package com.cityprojects.citybackend.controller.admin;

import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserResetPasswordResponseDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import com.cityprojects.citybackend.service.admin.DBUserAdminService;
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
 * Controller REST de gestion des utilisateurs par un SUPERADMIN.
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code GET /api/admin/users} : liste cross-hotel.</li>
 *   <li>{@code GET /api/admin/hotels/{hotelId}/users} : liste filtree par hotel.</li>
 *   <li>{@code GET /api/admin/hotels/{hotelId}/users/{userId}} : detail.</li>
 *   <li>{@code POST /api/admin/hotels/{hotelId}/users} : creation.</li>
 *   <li>{@code PUT /api/admin/hotels/{hotelId}/users/{userId}} : update.</li>
 *   <li>{@code POST /api/admin/hotels/{hotelId}/users/{userId}/verrouiller} : verrouillage compte.</li>
 *   <li>{@code POST /api/admin/hotels/{hotelId}/users/{userId}/deverrouiller} : deverrouillage.</li>
 *   <li>{@code POST /api/admin/hotels/{hotelId}/users/{userId}/reset-password} : reset password
 *       (retourne le mot de passe temp en clair une seule fois).</li>
 *   <li>{@code POST /api/admin/hotels/{hotelId}/users/{userId}/desactiver} : soft delete.</li>
 * </ul>
 *
 * <h2>hotelId via path-param : exception documentee</h2>
 * <p>Le {@code hotelId} arrive ici en path (pas dans le DTO ni le JWT) car
 * le SUPERADMIN agit cross-tenant et doit explicitement designer le tenant
 * cible. Securite garantie par {@code @PreAuthorize("hasRole('SUPERADMIN')")}
 * niveau classe. Cette exception ne s'applique <b>que</b> sous
 * {@code /api/admin/...} ; tout autre controller doit lire le hotelId
 * depuis le {@code TenantContext} (JWT).</p>
 *
 * <h2>Garde cross-hotel</h2>
 * <p>Les operations par {@code (hotelId, userId)} verifient cote service
 * que l'utilisateur appartient bien a l'hotel : sinon 404 (volontairement
 * pas 403 pour ne pas leak l'existence d'un user d'un autre hotel).</p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPERADMIN')")
public class DBUserAdminController {

    private final DBUserAdminService userAdminService;

    public DBUserAdminController(DBUserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<DBUserAdminDto>>> findAllUsersAllHotels(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.findAllUsersAllHotels(pageable)));
    }

    @GetMapping("/hotels/{hotelId}/users")
    public ResponseEntity<ApiResponse<Page<DBUserAdminDto>>> findAllByHotel(
            @PathVariable("hotelId") Long hotelId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.findAllByHotel(hotelId, pageable)));
    }

    @GetMapping("/hotels/{hotelId}/users/{userId}")
    public ResponseEntity<ApiResponse<DBUserAdminDto>> findById(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.findById(hotelId, userId)));
    }

    @PostMapping("/hotels/{hotelId}/users")
    public ResponseEntity<ApiResponse<DBUserAdminDto>> create(
            @PathVariable("hotelId") Long hotelId,
            @Valid @RequestBody DBUserCreateAdminDto dto) {
        DBUserAdminDto created = userAdminService.createForHotel(hotelId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Utilisateur cree"));
    }

    @PutMapping("/hotels/{hotelId}/users/{userId}")
    public ResponseEntity<ApiResponse<DBUserAdminDto>> update(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId,
            @Valid @RequestBody DBUserUpdateAdminDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                userAdminService.update(hotelId, userId, dto)));
    }

    @PostMapping("/hotels/{hotelId}/users/{userId}/verrouiller")
    public ResponseEntity<ApiResponse<Void>> verrouiller(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId) {
        userAdminService.verrouiller(hotelId, userId);
        return ResponseEntity.ok(ApiResponse.success("Compte verrouille"));
    }

    @PostMapping("/hotels/{hotelId}/users/{userId}/deverrouiller")
    public ResponseEntity<ApiResponse<Void>> deverrouiller(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId) {
        userAdminService.deverrouiller(hotelId, userId);
        return ResponseEntity.ok(ApiResponse.success("Compte deverrouille"));
    }

    @PostMapping("/hotels/{hotelId}/users/{userId}/reset-password")
    public ResponseEntity<ApiResponse<DBUserResetPasswordResponseDto>> resetPassword(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId) {
        DBUserResetPasswordResponseDto response = userAdminService.resetPassword(hotelId, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Mot de passe reinitialise"));
    }

    @PostMapping("/hotels/{hotelId}/users/{userId}/desactiver")
    public ResponseEntity<ApiResponse<Void>> desactiver(
            @PathVariable("hotelId") Long hotelId,
            @PathVariable("userId") Long userId) {
        userAdminService.desactiver(hotelId, userId);
        return ResponseEntity.ok(ApiResponse.success("Utilisateur desactive"));
    }
}
