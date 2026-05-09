package com.cityprojects.citybackend.entity.restaurant;

/**
 * Type de ticket emis par le POS restaurant (Tour 24).
 *
 * <ul>
 *   <li>{@code CAISSE} : ticket de caisse remis au client (preuve d'achat).</li>
 *   <li>{@code CUISINE} : ticket envoye au passe cuisine (liste des plats a
 *       preparer, sans prix).</li>
 *   <li>{@code REIMPRESSION} : duplicata d'un ticket precedent (cas perte
 *       papier, demande client). Necessite un motif.</li>
 * </ul>
 */
public enum TypeTicket {

    /** Ticket caisse client (avec prix et total). */
    CAISSE,

    /** Bon cuisine pour le passe (sans prix). */
    CUISINE,

    /** Reimpression d'un ticket existant (avec motif). */
    REIMPRESSION
}
