package com.cityprojects.citybackend.entity.finance;

/**
 * Type de compte (client direct ou societe B2B).
 *
 * <ul>
 *   <li>{@code CLIENT} : compte rattache a un client individuel
 *       (FK {@code client.clients}).</li>
 *   <li>{@code SOCIETE} : compte rattache a une societe B2B
 *       (FK {@code client.societes}).</li>
 * </ul>
 *
 * <p>Le type {@code FOURNISSEUR} du SQL legacy n'est PAS supporte au Tour 19 :
 * la dette fournisseur est traitee via les factures fournisseur
 * (FK {@code factures.fournisseur_id}), pas via un compte. Reporte au tour
 * finance-2 si besoin.</p>
 */
public enum TypeCompte {
    CLIENT,
    SOCIETE
}
