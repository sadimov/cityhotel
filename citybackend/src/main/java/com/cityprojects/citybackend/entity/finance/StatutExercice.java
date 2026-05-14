package com.cityprojects.citybackend.entity.finance;

/**
 * Statut d'un exercice comptable.
 *
 * <p>Transitions autorisées :</p>
 * <pre>
 *   OUVERT --&gt; EN_CLOTURE --&gt; CLOTURE
 *   (aucune transition de retour ; clôture définitive)
 * </pre>
 *
 * <ul>
 *   <li>{@code OUVERT} : exercice en cours - accepte les écritures (factures,
 *       paiements).</li>
 *   <li>{@code EN_CLOTURE} : exercice en cours de clôture - les écritures
 *       métier sont refusées ({@code error.exercice.cloture}). Permet de
 *       préparer les écritures de clôture (provisions, amortissements, etc.).</li>
 *   <li>{@code CLOTURE} : exercice définitivement clos - aucune modification
 *       possible.</li>
 * </ul>
 */
public enum StatutExercice {
    OUVERT,
    EN_CLOTURE,
    CLOTURE
}
