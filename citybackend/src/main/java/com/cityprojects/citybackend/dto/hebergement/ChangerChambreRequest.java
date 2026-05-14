package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour PATCH /api/hebergement/reservations/{id}/chambre
 * (Tour 44 Phase 1).
 *
 * <p>{@code ancienneChambreId} est requis pour identifier le pivot
 * {@link com.cityprojects.citybackend.entity.hebergement.ReservationChambre}
 * a modifier (une reservation peut comporter plusieurs chambres). Si
 * {@code null} et qu'il n'y a qu'un seul pivot, le service prend ce dernier.
 * Si plusieurs pivots et {@code ancienneChambreId} non specifie, le service
 * leve {@code error.reservation.changerChambre.ancienneChambre.required}.</p>
 *
 * <p>Aucune verification de l'identite de l'utilisateur cote payload : extrait
 * du SecurityContext.</p>
 */
public record ChangerChambreRequest(
        Long ancienneChambreId,

        @NotNull(message = "error.reservation.changerChambre.nouvelleChambre.required")
        Long nouvelleChambreId,

        @Size(max = 500, message = "error.reservation.changerChambre.raison.tooLong")
        String raison) {
}
