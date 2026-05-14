package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.RecapPaiementsReservationDto;

import java.math.BigDecimal;

/**
 * Service transverse hebergement -> finance (Tour 44 Phase 1).
 *
 * <p>Expose les vues agregees demandees par l'onglet "Paiements" du calendrier
 * des reservations sans dupliquer la logique entre {@code FactureService} et
 * {@code PaiementService}.</p>
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant}.</p>
 */
public interface ReservationFinanceService {

    /**
     * Recupere le recap complet des factures + paiements d'une reservation,
     * incluant les totaux global/paye/reste.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si la reservation n'existe pas pour le tenant courant.
     */
    RecapPaiementsReservationDto getRecapForReservation(Long reservationId);

    /**
     * Tour 45 : effectue le transfert comptable d'un check-out express. Pour
     * la facture liee a la reservation, le reste-a-payer est transfere du
     * compte client vers le compte societe (idempotent : ne fait rien si la
     * facture est deja PAYEE).
     *
     * <p>Strategie auxiliaire client (cf. doctrine Tour 20) :</p>
     * <ol>
     *   <li>DEBIT compte societe = {@code montantRestant} (la societe doit ce
     *       montant).</li>
     *   <li>CREDIT compte client = {@code montantRestant} (le client est solde).</li>
     *   <li>Mettre {@code Facture.montantPaye += montantRestant} et statut
     *       {@code PAYEE} (transfert assimile a un encaissement coté
     *       reservation).</li>
     * </ol>
     *
     * <p>Aucune facture ou plusieurs : leve {@code BusinessException}
     * ({@code error.checkoutExpress.factureNonTrouvee} /
     * {@code error.checkoutExpress.facturesMultiples}). Idempotent : si toutes
     * les factures sont deja PAYEE, retourne BigDecimal.ZERO sans action.</p>
     *
     * @return montant total transfere (somme des restants des factures
     *         eligibles), 0 si rien a faire.
     */
    BigDecimal applyCheckOutExpressTransfer(Long reservationId, Long clientId, Long societeId);
}
