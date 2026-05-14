package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Requête de mise à jour d'une configuration TVA (B4).
 *
 * <p>Le {@code typeService} arrive en path variable du controller, donc
 * non inclus ici.</p>
 *
 * @param taux    taux en pourcentage (0.00 à 99.99), obligatoire.
 * @param actif   nullable : si null, conserve la valeur courante.
 * @param libelle nullable : si null, conserve la valeur courante.
 */
public record TauxTvaConfigUpdateDto(
        @NotNull
        @DecimalMin(value = "0.00", message = "error.tva.taux.negatif")
        @DecimalMax(value = "99.99", message = "error.tva.taux.trop.eleve")
        BigDecimal taux,

        Boolean actif,

        @Size(max = 200)
        String libelle
) {}
