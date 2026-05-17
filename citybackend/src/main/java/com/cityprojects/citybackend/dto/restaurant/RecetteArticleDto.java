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
        Instant updatedAt,
        /** Nom de l'article (résolu côté service). */
        String nomArticle,
        /** Nom du produit (résolu côté service). */
        String nomProduit,
        /** Code du produit (résolu côté service). */
        String codeProduit) {

    public RecetteArticleDto withResolvedNames(String nomArt, String nomProd, String codeProd) {
        return new RecetteArticleDto(
                recetteId, articleId, produitId, quantiteParUnite, unite, note,
                actif, createdAt, updatedAt, nomArt, nomProd, codeProd);
    }
}
