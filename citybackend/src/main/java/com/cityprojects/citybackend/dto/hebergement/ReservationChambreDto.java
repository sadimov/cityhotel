package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie pour
 * {@link com.cityprojects.citybackend.entity.hebergement.ReservationChambre}.
 */
public record ReservationChambreDto(
        Long reservationChambreId,
        Long reservationId,
        Long chambreId,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal prixNuit) {
}
