package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO d'entree pour la reception d'une ligne de bon de commande.
 *
 * <p>Permet de servir partiellement (quantite &lt; quantite commandee) - la
 * difference reste a recevoir, le BC reste en {@code RECU_PARTIEL}.</p>
 */
public record ReceptionLigneDto(
        @NotNull(message = "error.reception.ligne.required")
        Long ligneId,

        @NotNull(message = "error.reception.quantite.required")
        @Min(value = 0, message = "error.reception.quantite.negative")
        Integer quantiteRecue) {
}
