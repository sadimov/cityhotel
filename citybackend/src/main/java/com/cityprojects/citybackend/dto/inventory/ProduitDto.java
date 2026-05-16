package com.cityprojects.citybackend.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.Produit}.
 *
 * <p>Inclut les champs derives {@code valeurStock} et {@code statutStock} pour
 * eviter aux clients front de re-calculer (cf. consignes_design_interface_graphique.txt).</p>
 */
public record ProduitDto(
        Long produitId,
        String codeProduit,
        String nomProduit,
        String description,
        Long categorieId,
        /** Resolu cote service (lookup CategorieProduit#getNomCategorie). */
        String nomCategorie,
        String uniteMesure,
        BigDecimal prixUnitaire,
        Integer seuilAlerte,
        Integer seuilCritique,
        Integer stockActuel,
        Long fournisseurPrincipalId,
        /** Resolu cote service (lookup Fournisseur#getNomFournisseur). */
        String nomFournisseurPrincipal,
        Boolean estFacturable,
        Boolean actif,
        BigDecimal valeurStock,
        String statutStock,
        Instant createdAt,
        Instant updatedAt) {
}
