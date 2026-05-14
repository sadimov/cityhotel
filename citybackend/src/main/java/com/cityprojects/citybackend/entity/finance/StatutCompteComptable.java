package com.cityprojects.citybackend.entity.finance;

/**
 * Statut d'un compte du Plan Comptable Général.
 *
 * <ul>
 *   <li>{@code ACTIF} : le compte peut être référencé par des écritures.</li>
 *   <li>{@code ARCHIVE} : le compte n'accepte plus de nouvelles écritures
 *       mais reste consultable pour l'historique.</li>
 * </ul>
 *
 * <p>Distinct du flag {@code utilisable} qui indique si le compte est un compte
 * de mouvement (vs. un compte de regroupement / titre dans la hiérarchie).</p>
 */
public enum StatutCompteComptable {
    ACTIF,
    ARCHIVE
}
