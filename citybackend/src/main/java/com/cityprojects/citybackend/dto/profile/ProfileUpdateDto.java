package com.cityprojects.citybackend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la mise a jour des infos personnelles via
 * {@code PUT /api/profile/me}.
 *
 * <p><b>Champs immutables (NON exposes ici) :</b>
 * <ul>
 *   <li>{@code username} : cle metier d'authent referencee dans les logs.</li>
 *   <li>{@code email} : workflow de validation hors scope (envoi token, etc.).</li>
 *   <li>{@code role}, {@code hotel}, {@code actif}, {@code compteVerrouille},
 *       {@code motPasseTemporaire} : reserves a l'admin (anti-escalation).</li>
 * </ul>
 * <p>Semantique non-PATCH ici : {@code prenom} et {@code nom} sont obligatoires
 * (un user ne peut pas avoir un nom vide en base). {@code telephone} et {@code poste}
 * sont optionnels (chaine vide = effacer).
 */
public record ProfileUpdateDto(
        @NotBlank(message = "error.user.prenom.blank")
        @Size(max = 100, message = "error.user.prenom.tooLong")
        String prenom,

        @NotBlank(message = "error.user.nom.blank")
        @Size(max = 100, message = "error.user.nom.tooLong")
        String nom,

        @Size(max = 20, message = "error.user.telephone.tooLong")
        String telephone,

        @Size(max = 100, message = "error.user.poste.tooLong")
        String poste) {
}
