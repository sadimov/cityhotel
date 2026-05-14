package com.cityprojects.citybackend.dto.reporting;

import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-INV-002 — Mouvements de stock valorises sur une periode.
 *
 * <p>Valorisation : {@code quantite * Produit.prixUnitaire} (le palier 1 ne
 * dispose pas de {@code prixUnitaireMoyen} ; fallback sur {@code prixUnitaire}).
 * Filtre type : tous, ENTREE seul, ou SORTIE seul.</p>
 *
 * @param from        borne inclusive
 * @param to          borne exclusive
 * @param typeFilter  filtre type (null = tous)
 * @param nbMouvements nombre total
 * @param valeurEntrees somme valorisee des ENTREE
 * @param valeurSorties somme valorisee des SORTIE+PERTE
 * @param lignes      detail des lignes
 */
public record MouvementValoriseDto(
        LocalDate from,
        LocalDate to,
        TypeMouvementStock typeFilter,
        Long nbMouvements,
        BigDecimal valeurEntrees,
        BigDecimal valeurSorties,
        List<MouvementLigneDto> lignes
) {

    /** Ligne valorisee d'un mouvement de stock. */
    public record MouvementLigneDto(
            Long mouvementId,
            Instant date,
            Long produitId,
            String codeProduit,
            String nomProduit,
            TypeMouvementStock typeMouvement,
            Integer quantite,
            BigDecimal prixUnitaire,
            BigDecimal valeur,
            String referenceDocument
    ) {
    }
}
