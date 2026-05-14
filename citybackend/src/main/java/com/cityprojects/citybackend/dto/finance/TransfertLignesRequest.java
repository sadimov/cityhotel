package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO d'entree (Tour 45) : transfert de lignes selectionnees d'une facture
 * source vers une facture cible.
 *
 * <p>Utilise typiquement pour reorganiser une facture (ex. deplacer des
 * lignes restaurant vers une facture dediee restaurant). Le transfert est
 * refuse si l'une des lignes a deja une affectation de paiement, ou si la
 * facture source ou cible est dans un etat terminal (PAYEE / ANNULEE).</p>
 */
public record TransfertLignesRequest(
        @NotEmpty List<Long> lignesIds,
        @NotNull Long factureCibleId) {
}
