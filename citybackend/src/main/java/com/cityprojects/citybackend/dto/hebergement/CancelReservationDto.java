package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload du POST {@code /api/hebergement/reservations/{id}/cancel}.
 *
 * <p>Le motif est obligatoire (regle metier : tout cancel doit etre tracable)
 * et borne a 500 caracteres pour rester aligne avec la taille de la colonne
 * {@code commentaires} et eviter une SQLException de longueur (Tour 12bis,
 * finding codeC-9).</p>
 */
public record CancelReservationDto(
        @NotBlank(message = "error.reservation.cancel.motif.required")
        @Size(max = 500, message = "error.reservation.cancel.motif.tooLong")
        String motif) {
}
