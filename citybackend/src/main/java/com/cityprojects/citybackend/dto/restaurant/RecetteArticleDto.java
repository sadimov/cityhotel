package com.cityprojects.citybackend.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.RecetteArticle}.
 *
 * <p>Pas de {@code hotelId} (resolu via TenantContext, jamais expose).</p>
 */
public record RecetteArticleDto(
        Long recetteId,
        Long articleId,
        Long produitId,
        BigDecimal quantiteParUnite,
        String unite,
        String note,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
