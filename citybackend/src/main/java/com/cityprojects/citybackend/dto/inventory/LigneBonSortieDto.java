package com.cityprojects.citybackend.dto.inventory;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.LigneBonSortie}.
 */
public record LigneBonSortieDto(
        Long ligneId,
        Long bonSortieId,
        Long produitId,
        Integer quantiteDemandee,
        Integer quantiteServie,
        String commentaires,
        /** Nom du produit (résolu côté service, anti-N+1). */
        String nomProduit,
        /** Code du produit (résolu côté service, anti-N+1). */
        String codeProduit,
        /** Unité de mesure du produit (résolue côté service). */
        String uniteMesure) {

    public LigneBonSortieDto withResolvedNames(String nomProd, String codeProd, String unite) {
        return new LigneBonSortieDto(
                ligneId, bonSortieId, produitId, quantiteDemandee, quantiteServie,
                commentaires, nomProd, codeProd, unite);
    }
}
