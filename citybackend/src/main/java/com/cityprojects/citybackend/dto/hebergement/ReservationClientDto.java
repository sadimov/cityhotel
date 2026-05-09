package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;

/**
 * DTO de sortie pour
 * {@link com.cityprojects.citybackend.entity.hebergement.ReservationClient}.
 */
public record ReservationClientDto(
        Long reservationClientId,
        Long reservationId,
        Long clientId,
        Long chambreId,
        Boolean estPayant,
        BigDecimal pourcentageCharge) {
}
