package com.cityprojects.citybackend.dto.restaurant;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.CategorieMenu}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext).</p>
 */
public record CategorieMenuDto(
        Long categorieId,
        String nom,
        String description,
        String iconeUrl,
        Integer ordre,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
