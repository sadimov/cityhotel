package com.cityprojects.citybackend.dto.inventory;

import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import jakarta.validation.constraints.NotNull;

/**
 * DTO d'entree pour ajuster manuellement le stock d'un produit
 * (correction d'inventaire, perte, etc.).
 *
 * <p>Le delta de quantite peut etre positif ou negatif. Le service garantit
 * que le stock final reste >= 0.</p>
 */
public record AjustementStockDto(
        @NotNull(message = "error.ajustement.type.required")
        TypeMouvementStock typeMouvement,

        @NotNull(message = "error.ajustement.quantite.required")
        Integer quantite,

        String commentaire) {
}
