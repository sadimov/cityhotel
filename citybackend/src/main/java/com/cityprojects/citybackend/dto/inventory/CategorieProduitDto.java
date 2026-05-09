package com.cityprojects.citybackend.dto.inventory;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.CategorieProduit}.
 */
public record CategorieProduitDto(
        Long categorieId,
        String codeCategorie,
        String nomCategorie,
        String description,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
