package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.StatutArticle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.ArticleMenu}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext).</p>
 */
public record ArticleMenuDto(
        Long articleId,
        String codeArticle,
        String nom,
        String description,
        Long categorieId,
        BigDecimal prix,
        String imageUrl,
        Boolean disponible,
        Boolean actif,
        StatutArticle statut,
        Instant createdAt,
        Instant updatedAt) {
}
