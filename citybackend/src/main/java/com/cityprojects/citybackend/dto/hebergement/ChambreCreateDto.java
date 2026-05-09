package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO de creation d'une chambre. Aucun {@code hotelId} : resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}.
 */
public record ChambreCreateDto(
        @NotBlank(message = "error.chambre.numero.blank")
        @Size(max = 10, message = "error.chambre.numero.tooLong")
        String numeroChambre,

        @NotNull(message = "error.chambre.type.required")
        Long typeId,

        Integer etage,

        /** Optionnel : statut initial (defaut DISPONIBLE cote service). */
        StatutChambre statut,

        @NotNull(message = "error.chambre.nbLits.required")
        @Positive(message = "error.chambre.nbLits.notPositive")
        Integer nbLits,

        @NotNull(message = "error.chambre.nbPersonnesMax.required")
        @Positive(message = "error.chambre.nbPersonnesMax.notPositive")
        Integer nbPersonnesMax,

        String equipements,

        String description) {
}
