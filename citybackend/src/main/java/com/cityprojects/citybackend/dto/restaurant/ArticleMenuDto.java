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
        Instant updatedAt,
        /** Nom de la catégorie (résolu côté service, anti-N+1). */
        String nomCategorie) {

    public ArticleMenuDto withResolvedNames(String nomCat) {
        return new ArticleMenuDto(
                articleId, codeArticle, nom, description, categorieId, prix, imageUrl,
                disponible, actif, statut, createdAt, updatedAt, nomCat);
    }
}
