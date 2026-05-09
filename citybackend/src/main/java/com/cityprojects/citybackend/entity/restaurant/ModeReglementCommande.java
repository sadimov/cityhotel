package com.cityprojects.citybackend.entity.restaurant;

/**
 * Mode de reglement d'une commande POS restaurant (Tour 24).
 *
 * <p>Cet enum n'est PAS un mode de paiement (cf.
 * {@link com.cityprojects.citybackend.entity.finance.ModePaiement}). Il
 * decrit la <b>nature</b> du reglement de la commande :</p>
 *
 * <ul>
 *   <li>{@code COMPTANT} : la commande est payee a la livraison via une
 *       Facture {@code FACTURE} + un {@code Paiement} (mode ESPECES, BANKILY,
 *       CARTE_BANCAIRE, ...). Une commande comptant peut donc etre encaissee
 *       avec n'importe quel mode de paiement.</li>
 *   <li>{@code REPORTE_CHAMBRE} : la commande est portee sur une reservation
 *       hebergement en cours ({@code Reservation.statut = ARRIVEE}). Le
 *       reglement effectif sera fait au check-out via la facture sejour
 *       (cf. TODO[tour-checkout-integration]).</li>
 * </ul>
 *
 * <h3>Stockage</h3>
 * <p>Colonne {@code mode_reglement VARCHAR(30) NOT NULL} sur
 * {@code restaurant.commandes}, mappee en {@link jakarta.persistence.EnumType#STRING}.</p>
 */
public enum ModeReglementCommande {

    /** Reglement immediat (cash, mobile money, CB) - cree une Facture+Paiement. */
    COMPTANT,

    /**
     * Reglement reporte sur la note d'une reservation hebergement.
     * La Facture sejour, generee au check-out, inclura les commandes reportees.
     */
    REPORTE_CHAMBRE
}
