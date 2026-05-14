package com.cityprojects.citybackend.controller.profile;

import com.cityprojects.citybackend.dto.profile.ChangePasswordDto;
import com.cityprojects.citybackend.dto.profile.ProfileDto;
import com.cityprojects.citybackend.dto.profile.ProfileUpdateDto;
import com.cityprojects.citybackend.service.profile.ProfileService;
import com.cityprojects.citybackend.util.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller REST du profil self-service.
 *
 * <h2>Securite</h2>
 * <p>{@code @PreAuthorize("isAuthenticated()")} au niveau classe — pas de
 * restriction de role, n'importe quel utilisateur authentifie peut gerer SON
 * profil. La garde anti-spoof (j'edite le profil d'un autre) est assuree
 * cote service : {@link ProfileService} resout TOUJOURS l'userId depuis
 * {@link com.cityprojects.citybackend.common.security.SecurityUtils}, jamais
 * depuis un path/query/body.</p>
 *
 * <h2>Pas de hotelId dans les URLs</h2>
 * <p>Tous les endpoints sont sur {@code /me/...} pour rendre evident qu'on
 * agit sur le user courant. C'est aussi la garantie multi-tenant : pas de
 * possibilite de traverser un tenant par URL.</p>
 */
@RestController
@RequestMapping("/api/profile")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileDto>> findCurrent() {
        return ResponseEntity.ok(ApiResponse.success(profileService.findCurrent()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileDto>> updateCurrent(
            @Valid @RequestBody ProfileUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.updateCurrent(dto), "Profil mis a jour"));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordDto dto) {
        profileService.changePassword(dto);
        return ResponseEntity.ok(ApiResponse.success("Mot de passe mis a jour"));
    }

    @PostMapping(path = "/me/avatar", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProfileDto>> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.uploadAvatar(file), "Avatar mis a jour"));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<ApiResponse<ProfileDto>> deleteAvatar() {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.deleteAvatar(), "Avatar supprime"));
    }
}
