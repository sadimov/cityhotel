package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree (Tour 45) : une demande de modification individuelle du montant
 * d'une nuitee.
 *
 * <p>Utilise en bloc par {@code PATCH /api/hebergement/nuitees/montants}
 * (body = {@code List<NuiteeMontantUpdateRequest>}).</p>
 *
 * <p>Si {@code ligneFactureId} est fourni, la ligne facture parente est
 * recalcule (montantHt / montantTtc) en preservant le {@code tauxTva}
 * actuel. {@code operationCompteId}, s'il est fourni, permet d'ajuster
 * l'operation DEBIT correspondante - sinon le service determine quoi faire
 * (typiquement aucune modification d'operation, car ce sont des audit-trails
 * immuables : la correction est portee par une nouvelle operation
 * d'ajustement, mais ce delta est trace dans le {@code soldeActuel} du
 * compte client via une mise a jour separee si necessaire).</p>
 */
public record NuiteeMontantUpdateRequest(
        @NotNull Long nuiteeId,
        @NotNull @DecimalMin(value = "0.0") BigDecimal nouveauMontant,
        Long ligneFactureId,
        Long operationCompteId) {
}
