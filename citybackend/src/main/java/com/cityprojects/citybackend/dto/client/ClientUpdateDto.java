package com.cityprojects.citybackend.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO d'entree pour la modification d'un {@link com.cityprojects.citybackend.entity.client.Client}.
 * <p>
 * Le {@code numeroClient} reste IMMUABLE apres creation (regle metier :
 * un numero genere par {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * ne se modifie jamais — sinon trous dans la sequence).
 * <p>
 * Le {@code actif} est explicite : passer {@code null} pour ne pas y toucher,
 * {@code true}/{@code false} pour reactiver/desactiver.
 */
public record ClientUpdateDto(
        @NotBlank(message = "error.client.prenom.blank")
        @Size(max = 100, message = "error.client.prenom.tooLong")
        String prenom,

        @NotBlank(message = "error.client.nom.blank")
        @Size(max = 100, message = "error.client.nom.tooLong")
        String nom,

        Long nationaliteId,

        @Size(max = 20, message = "error.client.telephone.tooLong")
        String telephone,

        @Email(message = "error.client.email.invalid")
        @Size(max = 100, message = "error.client.email.tooLong")
        String email,

        String adresse,

        @Size(max = 100, message = "error.client.ville.tooLong")
        String ville,

        @Size(max = 100, message = "error.client.pays.tooLong")
        String pays,

        Long typeIdentificationId,

        @Size(max = 50, message = "error.client.numeroIdentification.tooLong")
        String numeroIdentification,

        @Past(message = "error.client.dateNaissance.future")
        LocalDate dateNaissance,

        Long societeId,

        Boolean actif) {
}
