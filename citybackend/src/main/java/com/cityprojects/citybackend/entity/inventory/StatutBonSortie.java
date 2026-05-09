package com.cityprojects.citybackend.entity.inventory;

/**
 * Cycle de vie d'un bon de sortie de stock.
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code BROUILLON} -&gt; {@code VALIDE} : verification des disponibilites stock.</li>
 *   <li>{@code VALIDE} -&gt; {@code LIVRE} : livraison effective ; decremente le stock.</li>
 *   <li>{@code BROUILLON} | {@code VALIDE} -&gt; {@code ANNULE} : annulation
 *       (uniquement si LIVRE jamais atteint - sinon il faut creer un BS de retour).</li>
 * </ul>
 *
 * <p>Etats terminaux : {@code LIVRE}, {@code ANNULE}.</p>
 *
 * <h3>Impact stock</h3>
 * <p>Le stock est decremente uniquement a la livraison (transition vers LIVRE),
 * generant un MouvementStock de type SORTIE.</p>
 */
public enum StatutBonSortie {

    BROUILLON,
    VALIDE,
    LIVRE,
    ANNULE;

    /** Vrai si le BS est encore modifiable (lignes ajoutables/modifiables). */
    public static boolean peutEtreModifie(StatutBonSortie statut) {
        return statut == BROUILLON;
    }

    /** Vrai si le BS peut passer en LIVRE (statut courant doit etre VALIDE). */
    public static boolean peutEtreLivre(StatutBonSortie statut) {
        return statut == VALIDE;
    }

    /** Vrai si le BS peut etre annule (pas deja livre ni deja annule). */
    public static boolean peutEtreAnnule(StatutBonSortie statut) {
        return statut != LIVRE && statut != ANNULE;
    }
}
