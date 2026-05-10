package com.cityprojects.citybackend.dto.admin;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.core.Parametre}
 * cote administration SUPERADMIN.
 *
 * <p>{@code modifiable} expose : permet a l'IHM admin de griser les boutons
 * "modifier" / "supprimer" pour les parametres systeme proteges.</p>
 */
public record ParametreAdminDto(
        Long parametreId,
        String cle,
        String valeur,
        String description,
        Boolean modifiable,
        String categorie,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {
}
