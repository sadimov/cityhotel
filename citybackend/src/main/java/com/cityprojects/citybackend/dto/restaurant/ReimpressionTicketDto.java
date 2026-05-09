package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.TypeTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de demande de reimpression d'un ticket POS (Tour 24).
 *
 * <p>Le motif est obligatoire pour tracabilite (audit caisse). Le type
 * indique de quel ticket on emet le duplicata (CAISSE ou CUISINE).</p>
 */
public record ReimpressionTicketDto(
        @NotNull(message = "error.ticket.typeTicket.required")
        TypeTicket typeTicket,

        @NotBlank(message = "error.ticket.motif.required")
        @Size(max = 500, message = "error.ticket.motif.tooLong")
        String motif) {
}
