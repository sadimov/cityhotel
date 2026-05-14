package com.cityprojects.citybackend.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.ServiceHotelier}.
 */
public record ServiceHotelierDto(
        Long serviceId,
        Long typeServiceId,
        String code,
        String nom,
        String description,
        BigDecimal prixUnitaire,
        String unite,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
