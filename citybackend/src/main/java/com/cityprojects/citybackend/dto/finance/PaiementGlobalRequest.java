package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree (Tour 46) — paiement global d'une reservation sans selection
 * de lignes facture.
 *
 * <p>Le backend ventile automatiquement le montant sur toutes les lignes
 * facture <b>non payees</b> de la reservation, dans l'ordre FIFO :
 * <ol>
 *   <li>tri par {@code Facture.dateFacture} ASC puis {@code ligneFactureId} ASC,</li>
 *   <li>consommation sequentielle : chaque ligne recoit son reste complet
 *       jusqu'a epuisement du {@code montant},</li>
 *   <li>la derniere ligne touchee recoit le solde residuel du montant
 *       (potentiellement inferieur a son reste si le montant est insuffisant),</li>
 *   <li>excedent (montant &gt; somme des restes) : credite sur le compte client
 *       via {@code OperationCompteService.recordCredit} avec libelle "Avance solde
 *       {numeroPaiement}".</li>
 * </ol>
 *
 * <p>Difference avec {@link PaiementLignesRequest} Tour 45 :
 * <ul>
 *   <li>Tour 45 = selection explicite de lignes + ventilation
 *       <em>proportionnelle</em> au reste de chaque ligne.</li>
 *   <li>Tour 46 = ventilation <em>FIFO sequentielle</em> sur toutes les lignes
 *       non payees (plus naturel pour un encaissement global "j'ai recu X MRU
 *       pour cette reservation, ventile comme tu peux").</li>
 * </ul>
 *
 * <p>{@code idCompteClient} peut etre 0 ou {@code null} : le service le resout
 * via {@link com.cityprojects.citybackend.service.finance.CompteService#findOrCreateForClient(Long)}.</p>
 */
public record PaiementGlobalRequest(
        @NotNull Long reservationId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montant,
        @NotNull ModePaiement modePaiement,
        String motif,
        String description,
        @NotNull Long idClient,
        Long idCompteClient) {
}
