package com.cityprojects.citybackend.entity.inventory;

/**
 * Type de mouvement de stock (audit trail dans {@code inventory.mouvements_stock}).
 *
 * <ul>
 *   <li>{@code ENTREE} : reception d'un bon de commande (cf. BonCommandeService).</li>
 *   <li>{@code SORTIE} : livraison d'un bon de sortie (cf. BonSortieService).</li>
 *   <li>{@code AJUSTEMENT} : ajustement manuel d'inventaire (gain ou perte sur ecart constate).</li>
 *   <li>{@code PERTE} : perte / casse / peremption (sortie hors BS).</li>
 * </ul>
 */
public enum TypeMouvementStock {
    ENTREE,
    SORTIE,
    AJUSTEMENT,
    PERTE
}
