package com.cityprojects.citybackend.entity.finance;

/**
 * Statut comptable d'une facture.
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code BROUILLON} : facture creee, modifiable, non encore emise.</li>
 *   <li>{@code BROUILLON} -&gt; {@code EMISE} : facture validee, transmise au client.
 *       A partir de ce statut elle ne doit plus etre modifiee (audit comptable).</li>
 *   <li>{@code EMISE} -&gt; {@code PARTIELLEMENT_PAYEE} : un paiement partiel a ete
 *       affecte. {@code montant_paye &lt; montant_ttc}.</li>
 *   <li>{@code PARTIELLEMENT_PAYEE} -&gt; {@code PAYEE} : la somme des paiements
 *       affectes egale {@code montant_ttc}.</li>
 *   <li>{@code EMISE} -&gt; {@code PAYEE} : paiement direct integral.</li>
 *   <li>{@code BROUILLON} ou {@code EMISE} -&gt; {@code ANNULEE} : annulation
 *       (sans paiement). Une facture {@code ANNULEE} reste en base (audit).</li>
 * </ul>
 *
 * <p>Une facture deja {@code PARTIELLEMENT_PAYEE} ou {@code PAYEE} ne peut etre
 * annulee : elle doit donner lieu a un AVOIR (Facture avec
 * {@link TypeFacture#AVOIR}).</p>
 */
public enum StatutFacture {
    BROUILLON,
    EMISE,
    PARTIELLEMENT_PAYEE,
    PAYEE,
    ANNULEE
}
