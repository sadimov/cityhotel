package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detail jour-par-jour d'un calcul de montant : pour chaque date du sejour,
 * le prix applique (issu d'un tarif saisonnier ou du prix de base TypeChambre).
 */
public record MontantCalculDetailDto(
        LocalDate date,
        BigDecimal prix,
        String origine) {
}
