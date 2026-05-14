package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultat du compte de resultat SYSCOHADA simplifie (B5).
 *
 * <p>{@code resultatNet = totalProduits - totalCharges}.</p>
 * <p>{@code margeBrute = Sigma ventes (7061x-7068x) - Sigma achats consommes
 * (601x)}.</p>
 */
public record CompteResultatDto(
        Long exerciceId,
        String exerciceCode,
        LocalDate dateDebut,
        LocalDate dateFin,
        List<RubriqueResultatDto> produits,
        BigDecimal totalProduits,
        List<RubriqueResultatDto> charges,
        BigDecimal totalCharges,
        BigDecimal resultatNet,
        BigDecimal margeBrute,
        Instant generatedAt) {
}
