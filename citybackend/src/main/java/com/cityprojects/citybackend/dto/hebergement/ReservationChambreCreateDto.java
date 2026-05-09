package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO d'entree decrivant une chambre rattachee a une reservation lors de la
 * creation. Les dates {@code dateDebut}/{@code dateFin} sont optionnelles :
 * si null, le service prend respectivement {@code reservation.dateArrivee} /
 * {@code reservation.dateDepart}.
 */
public record ReservationChambreCreateDto(
        @NotNull(message = "error.reservationChambre.chambre.required")
        Long chambreId,

        LocalDate dateDebut,

        LocalDate dateFin,

        @NotNull(message = "error.reservationChambre.prixNuit.required")
        @DecimalMin(value = "0.0", message = "error.reservationChambre.prixNuit.negative")
        BigDecimal prixNuit) {
}
