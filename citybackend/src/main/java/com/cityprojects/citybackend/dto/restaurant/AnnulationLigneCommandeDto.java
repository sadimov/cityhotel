package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'annulation d'une ligne de commande POS (Tour 50).
 *
 * <p>Le motif est obligatoire pour tracabilite. Utilise pour retirer un plat
 * (ex. rupture cuisine, demande client) sans annuler la commande entiere.</p>
 *
 * <p>Refuse si la commande est SERVIE ou ANNULEE (etats terminaux), ou si
 * la commande est deja facturee.</p>
 */
public record AnnulationLigneCommandeDto(
        @NotBlank(message = "error.ligneCommande.motif.required")
        @Size(max = 500, message = "error.ligneCommande.motif.tooLong")
        String motif) {
}
