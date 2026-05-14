package com.cityprojects.citybackend.dto.inventory;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.TypeServiceHotelier}.
 */
public record TypeServiceHotelierDto(
        Long typeServiceId,
        String code,
        String nom,
        String description,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
