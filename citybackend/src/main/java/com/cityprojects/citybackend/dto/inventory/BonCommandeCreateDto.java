package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO d'entree pour la creation d'un bon de commande.
 *
 * <p><b>Aucun {@code numeroBc}</b> dans le payload : genere par le service via
 * {@code NumerotationService.next(BC)}. <b>Aucun {@code userId}</b> : extrait
 * du JWT/SecurityContext (createur).</p>
 */
public record BonCommandeCreateDto(
        @NotNull(message = "error.bonCommande.fournisseur.required")
        Long fournisseurId,

        LocalDate dateLivraisonPrevue,

        String commentaires,

        @NotEmpty(message = "error.bonCommande.lignes.empty")
        @Valid
        List<LigneBonCommandeCreateDto> lignes) {
}
