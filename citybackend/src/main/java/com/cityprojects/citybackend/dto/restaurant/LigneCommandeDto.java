package com.cityprojects.citybackend.dto.restaurant;

import java.math.BigDecimal;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.LigneCommande}.
 *
 * <p>Ne contient pas {@code hotelId} (resolu via TenantContext, jamais expose).</p>
 */
public record LigneCommandeDto(
        Long ligneId,
        Long commandeId,
        Long articleId,
        String libelle,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        BigDecimal montant,
        String notesCuisine) {
}
