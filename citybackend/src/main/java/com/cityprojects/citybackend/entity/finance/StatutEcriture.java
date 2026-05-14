package com.cityprojects.citybackend.entity.finance;

/**
 * Statut d'une ecriture comptable.
 *
 * <h3>Cycle de vie</h3>
 * <pre>
 *   (creation)
 *      |
 *      v
 *   BROUILLON  --validation-->  VALIDEE  --contre-passation-->  CONTRE_PASSEE
 * </pre>
 *
 * <ul>
 *   <li>{@link #BROUILLON} : ecriture en cours de saisie - permet edition.
 *       Pour les ecritures generees automatiquement par les services metier
 *       (FactureServiceImpl, PaiementServiceImpl), elles passent generalement
 *       directement en VALIDEE.</li>
 *   <li>{@link #VALIDEE} : ecriture posee comptablement. Les montants debit
 *       et credit sont equilibres. La ligne n'est plus modifiable (audit
 *       trail). Seule action possible : contre-passation.</li>
 *   <li>{@link #CONTRE_PASSEE} : ecriture annulee par une ecriture de
 *       contre-passation (sens inverses). Le lien est porte par
 *       {@code contrePasseeParId} (vers la nouvelle) et la contre-passation
 *       porte {@code ecritureSourceId} vers l'originale. Reciprocite stricte
 *       1-1.</li>
 * </ul>
 */
public enum StatutEcriture {

    /** En cours de saisie, modifiable. */
    BROUILLON,

    /** Validee, immuable, equilibree (Σ debits = Σ credits). */
    VALIDEE,

    /** Annulee par une contre-passation. */
    CONTRE_PASSEE
}
