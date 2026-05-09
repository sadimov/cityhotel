package com.cityprojects.citybackend.dto.inventory;

import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.MouvementStock}
 * (audit trail consultatif).
 */
public record MouvementStockDto(
        Long mouvementId,
        Long produitId,
        TypeMouvementStock typeMouvement,
        Integer quantite,
        BigDecimal prixUnitaire,
        Integer stockAvant,
        Integer stockApres,
        String referenceDocument,
        String commentaire,
        Long userId,
        Instant createdAt) {
}
