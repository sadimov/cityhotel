package com.cityprojects.citybackend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.LigneBonCommande}.
 */
public record LigneBonCommandeDto(
        Long ligneId,
        Long bonCommandeId,
        Long produitId,
        Integer quantiteCommandee,
        Integer quantiteRecue,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        LocalDate dateReception,
        /** Nom du produit (résolu côté service, anti-N+1). */
        String nomProduit,
        /** Code du produit (résolu côté service, anti-N+1). */
        String codeProduit,
        /** Unité de mesure du produit (résolue côté service). */
        String uniteMesure) {

    public LigneBonCommandeDto withResolvedNames(String nomProd, String codeProd, String unite) {
        return new LigneBonCommandeDto(
                ligneId, bonCommandeId, produitId, quantiteCommandee, quantiteRecue,
                prixUnitaire, sousTotal, dateReception, nomProd, codeProd, unite);
    }
}
