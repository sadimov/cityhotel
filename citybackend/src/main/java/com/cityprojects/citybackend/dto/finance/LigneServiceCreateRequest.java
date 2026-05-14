package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Tour 51bis : DTO d'entree du bridge ServiceHotelier -&gt; LigneFacture.
 *
 * <p>Permet d'ajouter une ligne {@code SERVICE} a une facture existante ou
 * a la facture (non payee) attachee a une reservation. Consomme par l'endpoint
 * {@code POST /api/finance/factures/lignes-service}.</p>
 *
 * <h3>Resolution de la facture cible</h3>
 * <ul>
 *   <li>{@code factureId} fourni -&gt; utilisation directe.</li>
 *   <li>Sinon {@code reservationId} fourni -&gt; resolution via
 *       {@code FactureRepository.findByReservationId} (selectionne la facture
 *       non terminale la plus recente).</li>
 *   <li>Sinon erreur {@code error.ligneService.targetRequired}.</li>
 * </ul>
 *
 * <h3>Refus metier</h3>
 * <ul>
 *   <li>{@code error.serviceHotelier.notFound} si le service n'existe pas (ou
 *       appartient a un autre tenant - filtre @TenantId Hibernate).</li>
 *   <li>{@code error.ligneService.serviceInactif} si {@code actif = false}.</li>
 *   <li>{@code error.facture.statut.cloturee} si la facture cible est en statut
 *       {@code PAYEE} ou {@code ANNULEE}.</li>
 * </ul>
 *
 * @param serviceId            identifiant du service hotelier (obligatoire).
 * @param reservationId        reservation cible (si {@code factureId} absent).
 * @param factureId            facture cible (prioritaire sur {@code reservationId}).
 * @param quantite             quantite consommee (positive).
 * @param prixUnitaireOverride si non null, remplace le prix catalogue.
 * @param description          libelle override (sinon {@code service.nom}).
 * @param tauxTva              taux TVA en pourcentage (null = 0).
 */
public record LigneServiceCreateRequest(
        @NotNull Long serviceId,
        Long reservationId,
        Long factureId,
        @NotNull @DecimalMin(value = "0.01", message = "error.ligneFacture.quantite.positive")
        BigDecimal quantite,
        @DecimalMin(value = "0.0", message = "error.ligneFacture.prix.negative")
        BigDecimal prixUnitaireOverride,
        String description,
        @DecimalMin(value = "0.0", message = "error.ligneFacture.tauxTva.negative")
        BigDecimal tauxTva) {
}
