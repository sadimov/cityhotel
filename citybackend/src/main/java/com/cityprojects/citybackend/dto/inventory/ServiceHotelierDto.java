package com.cityprojects.citybackend.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.ServiceHotelier}.
 *
 * <p>Conventions de nommage alignees sur le frontend (Tour 55b) : le contrat
 * JSON expose {@code codeService}, {@code nomService}, {@code uniteMesure} pour
 * coherence avec le module inventory frontend (admin services-hoteliers) et le
 * POS restaurant (Tour 55).</p>
 */
public record ServiceHotelierDto(
        Long serviceId,
        Long typeServiceId,
        String codeService,
        String nomService,
        String description,
        BigDecimal prixUnitaire,
        String uniteMesure,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
