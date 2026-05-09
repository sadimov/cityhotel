package com.cityprojects.citybackend.entity.finance;

/**
 * Type d'ecriture sur un compte (debit ou credit).
 *
 * <ul>
 *   <li>{@code DEBIT} : montant a la charge du compte (creation de facture).
 *       Augmente le solde du.</li>
 *   <li>{@code CREDIT} : montant en faveur du compte (paiement recu, avoir).
 *       Diminue le solde du.</li>
 * </ul>
 */
public enum TypeOperationCompte {
    DEBIT,
    CREDIT
}
