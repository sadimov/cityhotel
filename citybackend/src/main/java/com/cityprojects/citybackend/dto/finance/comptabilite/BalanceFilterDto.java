package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.time.LocalDate;

/**
 * Filtres d'execution du calcul de la balance comptable (B5).
 *
 * <p>Au moins un des deux modes doit etre fourni :</p>
 * <ul>
 *   <li>{@code exerciceId} - resout {@code dateDebut} / {@code dateFin} a partir
 *       des bornes de l'exercice (sauf si elles sont fournies explicitement,
 *       auquel cas elles l'emportent et permettent de calculer une balance
 *       partielle - ex. mois en cours d'un exercice en cours) ;</li>
 *   <li>{@code dateDebut} + {@code dateFin} - balance libre sur une plage.</li>
 * </ul>
 *
 * <p>{@code classe} (1-7) filtre optionnellement par classe SYSCOHADA.</p>
 */
public record BalanceFilterDto(
        Long exerciceId,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer classe) {
}
