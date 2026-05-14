package com.cityprojects.citybackend.entity.finance;

/**
 * Sens normal du solde d'un compte du Plan Comptable Général.
 *
 * <ul>
 *   <li>{@code DEBITEUR} : compte normalement soldé à débit (actif, charges).</li>
 *   <li>{@code CREDITEUR} : compte normalement soldé à crédit (passif, produits).</li>
 *   <li>{@code MIXTE} : sens variable selon le solde (trésorerie, résultat).</li>
 * </ul>
 *
 * <p>Utilisé pour la validation des écritures en partie double et pour les
 * contrôles d'audit (un compte CREDITEUR avec un solde négatif est un signal
 * d'anomalie à investiguer).</p>
 */
public enum SensNormal {
    DEBITEUR,
    CREDITEUR,
    MIXTE
}
