package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO d'entree pour reception (totale ou partielle) d'un bon de commande.
 *
 * <p>Le service additionne {@code quantiteRecue} a la {@code quantiteRecue}
 * cumulee de chaque ligne (gestion des receptions partielles successives) ;
 * le statut du BC passe a {@code RECU_PARTIEL} ou {@code RECU_COMPLET} selon
 * l'etat de toutes les lignes.</p>
 */
public record ReceptionBonCommandeDto(
        @NotEmpty(message = "error.reception.lignes.empty")
        @Valid
        List<ReceptionLigneDto> lignes) {
}
