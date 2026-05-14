package com.cityprojects.citybackend.entity.finance;

/**
 * Famille fonctionnelle d'un journal comptable.
 *
 * <p>Le SYSCOHADA mauritanien (et la pratique hoteliere) distingue plusieurs
 * journaux qui regroupent les ecritures par nature de flux :</p>
 * <ul>
 *   <li>{@link #VENTE} : journal des ventes (factures clients, services hoteliers,
 *       restauration). Compte de produit (classe 7) au credit, compte client
 *       au debit.</li>
 *   <li>{@link #ACHAT} : journal des achats (factures fournisseurs, charges).
 *       Compte de charge (classe 6) au debit, compte fournisseur au credit.</li>
 *   <li>{@link #TRESORERIE} : journal de tresorerie (banque, caisse, mobile
 *       money). Encaissements et decaissements.</li>
 *   <li>{@link #OPERATION_DIVERSE} : journal des operations diverses (OD) -
 *       ecritures qui n'entrent dans aucune autre famille (regularisations,
 *       reclassements, amortissements, abandons de creance, etc.).</li>
 *   <li>{@link #AVOIR} : journal des avoirs / notes de credit (annulations
 *       partielles ou totales de facture). Distinct du journal VENTE pour
 *       faciliter l'analyse.</li>
 * </ul>
 */
public enum TypeJournal {

    /** Journal des ventes (factures clients, services hoteliers, restauration). */
    VENTE,

    /** Journal des achats (factures fournisseurs, charges). */
    ACHAT,

    /** Journal de tresorerie (banque, caisse, mobile money). */
    TRESORERIE,

    /** Journal des operations diverses (regularisations, amortissements...). */
    OPERATION_DIVERSE,

    /** Journal des avoirs / notes de credit. */
    AVOIR
}
