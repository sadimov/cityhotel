package com.cityprojects.citybackend.entity.finance;

/**
 * Type d'une entree de la table {@code finance.factures}.
 *
 * <ul>
 *   <li>{@code FACTURE} : facture client standard (issue d'une reservation,
 *       d'une commande POS ou d'une vente directe).</li>
 *   <li>{@code AVOIR} : note de credit emise pour annuler tout ou partie d'une
 *       facture deja emise. {@code facture_reference_id} pointe sur la facture
 *       originale. Numerotation distincte (cf.
 *       {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#AVOIR}).</li>
 *   <li>{@code PROFORMA} : facture preliminaire (devis valant facture) - n'est
 *       pas comptabilisee. Reportee : non utilisee dans le scope du Tour 19.</li>
 *   <li>{@code FACTURE_FOURNISSEUR} : facture recue d'un fournisseur (achat),
 *       liee a un BonCommande inventory.</li>
 * </ul>
 */
public enum TypeFacture {
    FACTURE,
    AVOIR,
    PROFORMA,
    FACTURE_FOURNISSEUR
}
