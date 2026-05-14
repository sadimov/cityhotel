package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;

/**
 * Rapport R-INV-003b — Rotation produit sur une periode.
 *
 * <p>Calcul : {@code rotation = sorties / stockMoyen}. {@code stockMoyen} approxime
 * par {@code stockActuel} (le palier 1 n'historise pas l'inventaire).</p>
 */
public record RotationProduitDto(
        Long produitId,
        String codeProduit,
        String nomProduit,
        Long totalSorties,
        Integer stockActuel,
        BigDecimal rotation
) {
}
