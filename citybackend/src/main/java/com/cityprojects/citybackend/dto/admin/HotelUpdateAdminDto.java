package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la modification d'un hotel par un SUPERADMIN.
 * <p>
 * Tous les champs sont optionnels (semantique PATCH-like) : un champ
 * {@code null} signifie "ne pas modifier". Le {@code hotelCode} reste
 * <b>immuable</b> apres creation (cle metier referencee partout).
 * <p>
 * Pour activer/desactiver un hotel, utiliser les endpoints dedies
 * {@code POST /api/admin/hotels/{id}/desactiver} et
 * {@code POST /api/admin/hotels/{id}/reactiver} plutot que ce DTO.
 */
public record HotelUpdateAdminDto(
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
