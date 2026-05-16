package com.cityprojects.citybackend.dto.inventory;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation/modification d'un type de service hotelier.
 *
 * <p>Accepte les alias {@code codeType}/{@code nomType} pour retro-compatibilite
 * avec le front (qui utilise historiquement ces noms longs).</p>
 */
public record TypeServiceHotelierCreateDto(
        @JsonAlias({"codeType"})
        @NotBlank(message = "error.typeServiceHotelier.code.blank")
        @Size(max = 20, message = "error.typeServiceHotelier.code.tooLong")
        String code,

        @JsonAlias({"nomType"})
        @NotBlank(message = "error.typeServiceHotelier.nom.blank")
        @Size(max = 100, message = "error.typeServiceHotelier.nom.tooLong")
        String nom,

        String description) {
}
