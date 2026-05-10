package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la modification d'un parametre. La {@code cle} reste
 * immuable apres creation (cle metier referencee dans le code applicatif).
 *
 * <p>L'update est refuse cote service par {@code BusinessException(
 * "error.parametre.notModifiable")} si le parametre existant a
 * {@code modifiable=false}.</p>
 */
public record ParametreUpdateAdminDto(
        String valeur,

        @Size(max = 500, message = "error.parametre.description.tooLong")
        String description,

        @Size(max = 50, message = "error.parametre.categorie.tooLong")
        String categorie) {
}
