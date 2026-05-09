package com.cityprojects.citybackend.dto.inventory;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.Fournisseur}.
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} : ce champ ne transite jamais cote API.</p>
 */
public record FournisseurDto(
        Long fournisseurId,
        String nomFournisseur,
        String contactPrincipal,
        String telephone,
        String email,
        String adresse,
        String ville,
        String pays,
        String conditionsPaiement,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
