package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'un hotel par un SUPERADMIN.
 * <p>
 * Aucun {@code hotelId} dans le payload (genere par la base). Champs
 * essentiels uniquement (code, nom, devise, codePays) ; les autres champs
 * (adresse, telephone, ville, ...) sont optionnels et alimentables ulterieurement
 * via {@link HotelUpdateAdminDto}.
 * <p>
 * <b>codePays</b> : 2 lettres majuscules ISO 3166 (defaut "MR" cote service
 * si non fourni). Utilise pour le format de numerotation comptable
 * ({@code FACT-2026-MR-000123}).
 */
public record HotelCreateAdminDto(
        @NotBlank(message = "error.hotel.code.blank")
        @Size(max = 10, message = "error.hotel.code.tooLong")
        String hotelCode,

        @NotBlank(message = "error.hotel.nom.blank")
        @Size(max = 255, message = "error.hotel.nom.tooLong")
        String hotelNom,

        String hotelAdresse,

        @Size(max = 50, message = "error.hotel.tel.tooLong")
        String hotelTel,

        @Size(max = 100, message = "error.hotel.ville.tooLong")
        String ville,

        @Size(max = 100, message = "error.hotel.pays.tooLong")
        String pays,

        @Size(max = 20, message = "error.hotel.boitePostale.tooLong")
        String boitePostale,

        @Email(message = "error.hotel.email.invalid")
        @Size(max = 100, message = "error.hotel.email.tooLong")
        String email,

        @Size(max = 200, message = "error.hotel.siteWeb.tooLong")
        String siteWeb,

        @Size(min = 3, max = 3, message = "error.hotel.devise.invalid")
        String devise,

        @Pattern(regexp = "^[A-Z]{2}$", message = "error.hotel.codePays.invalid")
        String codePays,

        @Size(max = 50, message = "error.hotel.fuseauHoraire.tooLong")
        String fuseauHoraire,

        @Size(max = 500, message = "error.hotel.logoUrl.tooLong")
        String logoUrl) {
}
