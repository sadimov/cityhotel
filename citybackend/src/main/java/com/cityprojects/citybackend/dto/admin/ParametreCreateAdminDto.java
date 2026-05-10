package com.cityprojects.citybackend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation d'un parametre applicatif global par un
 * SUPERADMIN.
 *
 * <p>Pas de champ {@code modifiable} dans ce DTO : tout parametre cree via
 * l'API est forcement {@code modifiable=true} cote service. Les parametres
 * <b>systeme</b> ({@code modifiable=false}) ne peuvent etre crees que par
 * un changeset Liquibase (cf. {@code 029-create-admin-parametres-schema.xml}).
 * Cette regle protege la configuration de production contre les
 * fausses-manipulations.</p>
 */
public record ParametreCreateAdminDto(
        @NotBlank(message = "error.parametre.cle.blank")
        @Size(max = 80, message = "error.parametre.cle.tooLong")
        String cle,

        String valeur,

        @Size(max = 500, message = "error.parametre.description.tooLong")
        String description,

        @Size(max = 50, message = "error.parametre.categorie.tooLong")
        String categorie) {
}
