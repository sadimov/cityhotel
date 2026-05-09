package com.cityprojects.citybackend.entity.finance;

/**
 * Statut d'un paiement encaisse.
 *
 * <ul>
 *   <li>{@code VALIDE} : paiement encaisse et confirme (etat le plus courant).</li>
 *   <li>{@code EN_ATTENTE} : encaissement en cours (paiement par cheque non
 *       encore credite, virement en cours).</li>
 *   <li>{@code REFUSE} : paiement rejete par la banque ou le wallet.</li>
 *   <li>{@code ANNULE} : annulation manuelle. Le paiement reste en base mais
 *       n'impacte plus les soldes (audit). Possible uniquement si aucune
 *       affectation n'a deja ete propagee a une facture (cf.
 *       PaiementService.annuler()).</li>
 * </ul>
 */
public enum StatutPaiement {
    VALIDE,
    EN_ATTENTE,
    REFUSE,
    ANNULE
}
