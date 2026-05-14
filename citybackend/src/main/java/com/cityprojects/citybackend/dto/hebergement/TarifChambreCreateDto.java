package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO d'entree pour la creation/modification d'un tarif saisonnier.
 *
 * <p>Aucun {@code hotelId} dans le payload (resolver Hibernate). La coherence
 * {@code dateFin >= dateDebut} est validee cote service.</p>
 */
public record TarifChambreCreateDto(
        @NotNull(message = "error.tarifChambre.typeId.required")
        Long typeId,

        @NotBlank(message = "error.tarifChambre.nomTarif.blank")
        @Size(max = 100, message = "error.tarifChambre.nomTarif.tooLong")
        String nomTarif,

        @NotNull(message = "error.tarifChambre.dateDebut.required")
        LocalDate dateDebut,

        LocalDate dateFin,

        @NotNull(message = "error.tarifChambre.prixNuit.required")
        @DecimalMin(value = "0.0", message = "error.tarifChambre.prixNuit.negative")
        BigDecimal prixNuit,

        @DecimalMin(value = "0.0", message = "error.tarifChambre.prixWeekend.negative")
        BigDecimal prixWeekend,

        @PositiveOrZero(message = "error.tarifChambre.priorite.negative")
        Integer priorite,

        Boolean actif) {
}
