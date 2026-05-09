package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.hebergement.TypeChambre}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 */
public record TypeChambreDto(
        Long typeId,
        String typeCode,
        String typeNom,
        String description,
        BigDecimal superficie,
        Integer nbLitsMax,
        Integer nbPersonnesMax,
        BigDecimal prixBase,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
