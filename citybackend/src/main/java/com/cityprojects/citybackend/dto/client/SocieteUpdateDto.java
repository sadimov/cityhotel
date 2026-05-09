package com.cityprojects.citybackend.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la modification d'une {@link com.cityprojects.citybackend.entity.client.Societe}.
 * <p>
 * Le {@code societeId} et le {@code hotelId} sont issus du serveur (path
 * variable + TenantContext), jamais du payload.
 * Le flag {@code actif} est explicitement gere ici pour permettre la
 * reactivation/desactivation via l'endpoint UPDATE generique. Pour ne
 * PAS toucher a l'etat actif, omettre le champ ({@code null}).
 */
public record SocieteUpdateDto(
        @NotBlank(message = "error.societe.nom.blank")
        @Size(max = 255, message = "error.societe.nom.tooLong")
        String societeNom,

        @Size(max = 20, message = "error.societe.siret.tooLong")
        String siret,

        String adresse,

        @Size(max = 100, message = "error.societe.ville.tooLong")
        String ville,

        @Size(max = 100, message = "error.societe.pays.tooLong")
        String pays,

        @Size(max = 20, message = "error.societe.telephone.tooLong")
        String telephone,

        @Email(message = "error.societe.email.invalid")
        @Size(max = 100, message = "error.societe.email.tooLong")
        String email,

        @Size(max = 200, message = "error.societe.contactPrincipal.tooLong")
        String contactPrincipal,

        Boolean actif) {
}
