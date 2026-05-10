package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'un utilisateur par un SUPERADMIN.
 * <p>
 * <b>Pas de {@code hotelId}</b> dans le payload : le hotelId est positionne
 * via le path-param {@code /api/admin/hotels/{hotelId}/users}. C'est une
 * <b>exception documentee</b> a la regle generale "pas de hotelId via HTTP" :
 * le SUPERADMIN gere par definition les utilisateurs cross-tenant et doit
 * pouvoir designer le tenant cible. La securite est garantie par
 * {@code @PreAuthorize("hasRole('SUPERADMIN')")} et la chaine
 * {@code TenantScope.runAs(hotelId, ...)} cote service.
 *
 * <p>{@code roleId} est obligatoire : un user sans role n'est pas exploitable
 * par la chaine d'authorization Spring Security.</p>
 */
public record DBUserCreateAdminDto(
        @NotBlank(message = "error.user.username.blank")
        @Size(min = 3, max = 100, message = "error.user.username.size")
        String username,

        @NotBlank(message = "error.user.email.blank")
        @Email(message = "error.user.email.invalid")
        @Size(max = 100, message = "error.user.email.tooLong")
        String email,

        /**
         * Mot de passe en clair. Hash BCrypt cote service via {@link
         * com.cityprojects.citybackend.util.PasswordUtil#hashPassword(String)}.
         * Au moins 8 caracteres (validation finale via PasswordUtil).
         */
        @NotBlank(message = "error.user.password.blank")
        @Size(min = 8, max = 128, message = "error.user.password.size")
        String password,

        @NotBlank(message = "error.user.prenom.blank")
        @Size(max = 100, message = "error.user.prenom.tooLong")
        String prenom,

        @NotBlank(message = "error.user.nom.blank")
        @Size(max = 100, message = "error.user.nom.tooLong")
        String nom,

        @Size(max = 20, message = "error.user.telephone.tooLong")
        String telephone,

        @Size(max = 100, message = "error.user.poste.tooLong")
        String poste,

        @NotNull(message = "error.user.roleId.null")
        Integer roleId) {
}
