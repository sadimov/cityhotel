package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO d'entree pour la creation/modification d'un type de service hotelier.
 */
public record TypeServiceHotelierCreateDto(
        @NotBlank(message = "error.typeServiceHotelier.code.blank")
        @Size(max = 20, message = "error.typeServiceHotelier.code.tooLong")
        String code,

        @NotBlank(message = "error.typeServiceHotelier.nom.blank")
        @Size(max = 100, message = "error.typeServiceHotelier.nom.tooLong")
        String nom,

        String description) {
}
