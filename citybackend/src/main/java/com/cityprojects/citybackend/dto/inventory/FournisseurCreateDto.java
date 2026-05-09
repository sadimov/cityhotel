package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'un fournisseur.
 *
 * <p><b>Aucun {@code hotelId}</b> dans le payload : extrait du TenantContext.</p>
 */
public record FournisseurCreateDto(
        @NotBlank(message = "error.fournisseur.nom.blank")
        @Size(max = 255, message = "error.fournisseur.nom.tooLong")
        String nomFournisseur,

        @Size(max = 200, message = "error.fournisseur.contact.tooLong")
        String contactPrincipal,

        @Size(max = 20, message = "error.fournisseur.telephone.tooLong")
        String telephone,

        @Email(message = "error.fournisseur.email.invalid")
        @Size(max = 100, message = "error.fournisseur.email.tooLong")
        String email,

        String adresse,

        @Size(max = 100, message = "error.fournisseur.ville.tooLong")
        String ville,

        @Size(max = 100, message = "error.fournisseur.pays.tooLong")
        String pays,

        String conditionsPaiement) {
}
