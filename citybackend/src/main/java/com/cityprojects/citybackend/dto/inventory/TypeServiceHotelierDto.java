package com.cityprojects.citybackend.dto.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.TypeServiceHotelier}.
 *
 * <p>Expose aussi {@code codeType} / {@code nomType} pour compatibilite avec
 * le front qui utilise ces noms longs (cf. model TypeServiceHotelier TS).</p>
 */
public record TypeServiceHotelierDto(
        Long typeServiceId,
        String code,
        String nom,
        String description,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {

    @JsonProperty("codeType")
    public String getCodeType() {
        return code;
    }

    @JsonProperty("nomType")
    public String getNomType() {
        return nom;
    }
}
