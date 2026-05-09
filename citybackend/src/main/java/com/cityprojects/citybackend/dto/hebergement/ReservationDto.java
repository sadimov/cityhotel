package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.StatutReservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO de sortie pour
 * {@link com.cityprojects.citybackend.entity.hebergement.Reservation}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 */
public record ReservationDto(
        Long reservationId,
        String numeroReservation,
        Long clientPrincipalId,
        Long societeId,
        LocalDate dateArrivee,
        LocalDate dateDepart,
        Integer nbNuits,
        Integer nbAdultes,
        Integer nbEnfants,
        StatutReservation statut,
        String motifSejour,
        String commentaires,
        BigDecimal reductionPourcentage,
        BigDecimal montantTotal,
        Long userId,
        Instant createdAt,
        Instant updatedAt) {
}
