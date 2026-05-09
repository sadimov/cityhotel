package com.cityprojects.citybackend.entity.inventory;

/**
 * Cycle de vie d'un bon de commande fournisseur.
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code BROUILLON} -&gt; {@code ENVOYE} : envoi au fournisseur (lignes figees).</li>
 *   <li>{@code ENVOYE} -&gt; {@code CONFIRME} : accuse de reception du fournisseur.</li>
 *   <li>{@code CONFIRME} -&gt; {@code RECU_PARTIEL} : reception partielle (au moins une ligne servie).</li>
 *   <li>{@code RECU_PARTIEL} -&gt; {@code RECU_COMPLET} : derniere reception (toutes lignes servies).</li>
 *   <li>{@code CONFIRME} -&gt; {@code RECU_COMPLET} : reception en une fois.</li>
 *   <li>{@code BROUILLON} | {@code ENVOYE} | {@code CONFIRME} | {@code RECU_PARTIEL} -&gt; {@code ANNULE} :
 *       annulation, conditionnee par {@link #peutEtreAnnule(StatutBonCommande)}.</li>
 * </ul>
 *
 * <p>Etats terminaux : {@code RECU_COMPLET}, {@code ANNULE}.</p>
 *
 * <h3>Impact stock</h3>
 * <p>Le stock n'est pas impacte tant que le BC n'est pas receptionne. La reception
 * (transition vers RECU_PARTIEL ou RECU_COMPLET) genere un MouvementStock de type
 * ENTREE et incremente {@code Produit.stockActuel} du delta {@code quantiteRecue}.</p>
 */
public enum StatutBonCommande {

    BROUILLON,
    ENVOYE,
    CONFIRME,
    RECU_PARTIEL,
    RECU_COMPLET,
    ANNULE;

    /** Vrai si le BC est encore modifiable (lignes ajoutables/modifiables). */
    public static boolean peutEtreModifie(StatutBonCommande statut) {
        return statut == BROUILLON;
    }

    /** Vrai si le BC peut etre annule (pas encore termine ni deja annule). */
    public static boolean peutEtreAnnule(StatutBonCommande statut) {
        return statut != RECU_COMPLET && statut != ANNULE;
    }
}
