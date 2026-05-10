package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la modification d'un utilisateur par un SUPERADMIN.
 * <p>
 * Tous les champs sont optionnels (semantique PATCH). Le mot de passe
 * <b>n'est PAS</b> modifiable via cet endpoint : utiliser
 * {@code POST /api/admin/hotels/{hotelId}/users/{userId}/reset-password}
 * qui regenere un mot de passe temporaire (retourne en clair une seule fois).
 * <p>
 * Le {@code username} reste immuable apres creation (cle metier d'authent
 * referencee dans les logs et les sessions).
 */
public record DBUserUpdateAdminDto(
        @Email(message = "error.user.email.invalid")
        @Size(max = 100, message = "error.user.email.tooLong")
        String email,

        @Size(max = 100, message = "error.user.prenom.tooLong")
        String prenom,

        @Size(max = 100, message = "error.user.nom.tooLong")
        String nom,

        @Size(max = 20, message = "error.user.telephone.tooLong")
        String telephone,

        @Size(max = 100, message = "error.user.poste.tooLong")
        String poste,

        Integer roleId) {
}
