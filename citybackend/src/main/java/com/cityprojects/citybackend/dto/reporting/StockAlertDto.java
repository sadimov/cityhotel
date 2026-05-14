package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;

/**
 * Rapport R-INV-001 — Alerte stock pour un produit donne (un produit = une ligne).
 *
 * <p>Calcule a la volee sur {@code inventory.produits} : produits actifs dont
 * {@code stock_actuel &lt;= seuil_alerte}. Le statut "CRITIQUE" vs "ALERTE" est
 * positionne cote service pour eviter de dupliquer la regle dans la projection JPQL.</p>
 *
 * @param produitId        FK produit
 * @param codeProduit      code metier (unique par hotel)
 * @param nomProduit       libelle
 * @param uniteMesure      ex. "kg", "L", "piece"
 * @param stockActuel      stock courant
 * @param seuilAlerte      seuil bas (declenchement notification)
 * @param seuilCritique    seuil tres bas (declenchement reapprovisionnement urgent)
 * @param ecart            seuilAlerte - stockActuel (positif = sous seuil)
 * @param statut           "CRITIQUE" si stockActuel <= seuilCritique, sinon "ALERTE"
 * @param valeurManquante  ecart * prixUnitaire (estimation du reapprovisionnement)
 */
public record StockAlertDto(
        Long produitId,
        String codeProduit,
        String nomProduit,
        String uniteMesure,
        Integer stockActuel,
        Integer seuilAlerte,
        Integer seuilCritique,
        Integer ecart,
        String statut,
        BigDecimal valeurManquante
) {
}
