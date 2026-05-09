package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.TypeTicket;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.Ticket} (Tour 24).
 *
 * <p>Ne contient pas {@code hotelId} (resolu via TenantContext).</p>
 */
public record TicketDto(
        Long ticketId,
        Long commandeId,
        TypeTicket typeTicket,
        Instant dateImpression,
        Long imprimeParUserId,
        String motifReimpression) {
}
