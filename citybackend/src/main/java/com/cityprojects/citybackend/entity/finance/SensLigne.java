package com.cityprojects.citybackend.entity.finance;

/**
 * Sens d'une ligne d'ecriture comptable en partie double.
 *
 * <p>Une ligne d'ecriture est <b>soit</b> au debit (D), <b>soit</b> au credit
 * (C). Le montant est toujours positif - le sens porte l'information
 * directionnelle. La somme des debits doit egaler la somme des credits sur une
 * meme ecriture (regle de la partie double, validee par
 * {@code EcritureComptableService} avant passage du statut VALIDEE).</p>
 */
public enum SensLigne {

    /** Ligne au debit (D). */
    DEBIT,

    /** Ligne au credit (C). */
    CREDIT
}
