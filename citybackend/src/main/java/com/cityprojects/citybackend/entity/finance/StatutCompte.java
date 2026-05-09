package com.cityprojects.citybackend.entity.finance;

/**
 * Statut d'un compte client/societe.
 *
 * <ul>
 *   <li>{@code ACTIF} : peut etre debite/credite normalement.</li>
 *   <li>{@code SUSPENDU} : aucune nouvelle facture / aucun nouveau paiement.
 *       Les factures existantes peuvent encore etre payees.</li>
 *   <li>{@code FERME} : compte cloture, archivage uniquement.</li>
 * </ul>
 */
public enum StatutCompte {
    ACTIF,
    SUSPENDU,
    FERME
}
