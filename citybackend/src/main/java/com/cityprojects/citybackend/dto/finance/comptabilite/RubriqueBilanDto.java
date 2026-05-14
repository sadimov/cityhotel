package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rubrique du bilan (B5) - regroupement metier de comptes.
 *
 * <p>Exemples :</p>
 * <ul>
 *   <li>ACTIF : "Immobilisations corporelles" (classe 2), "Stocks" (classe 3),
 *       "Creances" (classe 4 ACTIF), "Tresorerie-Actif" (classe 5 debiteur) ;</li>
 *   <li>PASSIF : "Capitaux propres" (10x, 12x), "Resultat de l'exercice"
 *       (calcule), "Dettes circulantes" (classe 4 PASSIF), "Tresorerie-Passif"
 *       (classe 5 crediteur).</li>
 * </ul>
 */
public record RubriqueBilanDto(
        String code,
        String libelle,
        int classe,
        List<LigneBilanDto> lignes,
        BigDecimal montant) {
}
