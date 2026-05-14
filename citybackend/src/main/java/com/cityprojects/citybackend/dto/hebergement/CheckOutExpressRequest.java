package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.NotNull;

/**
 * DTO d'entree (Tour 45) : check-out express d'une reservation avec transfert
 * du reste-a-payer sur le compte d'une societe.
 *
 * <p>Workflow :</p>
 * <ol>
 *   <li>Verifier statut reservation = {@code ARRIVEE}.</li>
 *   <li>Recuperer la facture liee a la reservation.</li>
 *   <li>Pour chaque ligne non payee : creer une operation DEBIT sur le
 *       compte societe = montant_restant.</li>
 *   <li>Boucler le solde cote client : creer une operation CREDIT sur le
 *       compte client (motif "Transfert a societe").</li>
 *   <li>Marquer la facture {@code PARTIELLEMENT_PAYEE} ou {@code PAYEE}.</li>
 *   <li>Reservation -&gt; {@code PARTIE}, chambres -&gt; {@code NETTOYAGE}.</li>
 * </ol>
 */
public record CheckOutExpressRequest(
        @NotNull Long societeId,
        Long clientId) {
}
