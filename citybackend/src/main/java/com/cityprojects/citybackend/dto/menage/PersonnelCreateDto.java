package com.cityprojects.citybackend.dto.menage;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO d'entree pour la creation/modification d'un agent de menage.
 *
 * <p>Aucun {@code hotelId} : resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}.</p>
 */
public record PersonnelCreateDto(
        @NotBlank(message = "error.personnel.numeroEmploye.blank")
        @Size(max = 20, message = "error.personnel.numeroEmploye.tooLong")
        String numeroEmploye,

        @NotBlank(message = "error.personnel.prenom.blank")
        @Size(max = 100, message = "error.personnel.prenom.tooLong")
        String prenom,

        @NotBlank(message = "error.personnel.nom.blank")
        @Size(max = 100, message = "error.personnel.nom.tooLong")
        String nom,

        @Size(max = 20, message = "error.personnel.telephone.tooLong")
        String telephone,

        @Email(message = "error.personnel.email.invalid")
        @Size(max = 100, message = "error.personnel.email.tooLong")
        String email,

        LocalDate dateEmbauche,

        @Size(max = 500, message = "error.personnel.specialites.tooLong")
        String specialites) {
}
