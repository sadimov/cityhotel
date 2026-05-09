package com.cityprojects.citybackend.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'une {@link com.cityprojects.citybackend.entity.client.Societe}.
 * <p>
 * <b>Aucun {@code hotelId}</b> : il vient du {@link com.cityprojects.citybackend.common.tenant.TenantContext}
 * cote serveur, jamais d'un payload HTTP (CLAUDE.md racine §10).
 */
public record SocieteCreateDto(
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
        String contactPrincipal) {
}
