package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree pour ajouter un client additionnel a une reservation.
 */
public record ReservationClientCreateDto(
        @NotNull(message = "error.reservationClient.client.required")
        Long clientId,

        Long chambreId,

        Boolean estPayant,

        @DecimalMin(value = "0.00", message = "error.reservationClient.pourcentage.negative")
        @DecimalMax(value = "100.00", message = "error.reservationClient.pourcentage.tooHigh")
        BigDecimal pourcentageCharge) {
}
