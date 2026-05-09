package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'annulation d'une commande POS (Tour 24).
 *
 * <p>Le motif est obligatoire pour tracabilite. Une commande SERVIE ne peut
 * pas etre annulee (refus cote service - faire un avoir si remboursement).</p>
 */
public record AnnulationCommandeDto(
        @NotBlank(message = "error.commande.motif.required")
        @Size(max = 500, message = "error.commande.motif.tooLong")
        String motif) {
}
