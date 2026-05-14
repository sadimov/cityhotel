package com.cityprojects.citybackend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour {@code POST /api/profile/me/change-password}.
 *
 * <p>Le service verifie en plus, au-dela des contraintes Bean Validation :
 * <ul>
 *   <li>{@code ancienMotDePasse} matche le hash BCrypt persiste (sinon
 *       {@code BusinessException("error.user.password.invalid")}).</li>
 *   <li>{@code nouveauMotDePasse.equals(confirmation)} (sinon
 *       {@code BusinessException("error.user.password.mismatch")}).</li>
 *   <li>{@code nouveauMotDePasse != ancienMotDePasse} (sinon
 *       {@code BusinessException("error.user.password.unchanged")}).</li>
 *   <li>{@link com.cityprojects.citybackend.util.PasswordUtil#validatePassword(String)}
 *       passe (sinon {@code BusinessException("error.user.password.weak")}).</li>
 * </ul>
 *
 * <p>Apres rotation reussie, {@code motPasseTemporaire} est forcee a {@code false}
 * cote service (cf. flow Tour 38 C8).
 */
public record ChangePasswordDto(
        @NotBlank(message = "error.user.password.ancien.blank")
        String ancienMotDePasse,

        @NotBlank(message = "error.user.password.nouveau.blank")
        @Size(min = 8, max = 128, message = "error.user.password.size")
        String nouveauMotDePasse,

        @NotBlank(message = "error.user.password.confirmation.blank")
        String confirmation) {
}
