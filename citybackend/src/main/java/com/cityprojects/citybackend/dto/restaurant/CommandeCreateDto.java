package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO d'entree pour la creation d'une commande POS (Tour 24).
 *
 * <p>Aucun {@code hotelId} : resolu via {@code TenantContext}.</p>
 *
 * <h3>Regles de coherence</h3>
 * <ul>
 *   <li>Si {@code modeReglement = REPORTE_CHAMBRE}, alors {@code reservationId}
 *       est obligatoire et la reservation doit etre en statut {@code ARRIVEE}
 *       (validation cote service).</li>
 *   <li>Si {@code modeReglement = COMPTANT}, {@code reservationId} doit etre
 *       null (sinon le service leve {@code BusinessException}).</li>
 *   <li>{@code clientId} reste optionnel dans tous les cas (POS walk-in).</li>
 *   <li>Au moins une ligne est obligatoire (commande vide refusee).</li>
 * </ul>
 */
public record CommandeCreateDto(
        @NotNull(message = "error.commande.modeReglement.required")
        ModeReglementCommande modeReglement,

        Long clientId,

        Long reservationId,

        @Size(max = 3)
        String devise,

        /**
         * Numero de table physique (optionnel, Tour 26.1). Ex. "T12", "Salon-3".
         * Null pour commandes a emporter / livraison / sans table assignee.
         * Decision 1=A : simple chaine, pas d'entite Table.
         */
        @Size(max = 20, message = "error.commande.numeroTable.tropLong")
        String numeroTable,

        @NotEmpty(message = "error.commande.lignes.empty")
        @Valid
        List<LigneCommandeCreateDto> lignes) {
}
