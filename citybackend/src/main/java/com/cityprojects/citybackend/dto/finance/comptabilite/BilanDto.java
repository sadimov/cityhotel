package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultat du bilan SYSCOHADA simplifie (B5).
 *
 * <p>{@code totalActif} doit egaler {@code totalPassif} (resultat net inclus
 * dans le passif). Ecart au-dela de {@code 0.01} MRU declenche un log WARN.</p>
 */
public record BilanDto(
        Long exerciceId,
        String exerciceCode,
        LocalDate dateArrete,
        List<RubriqueBilanDto> actif,
        BigDecimal totalActif,
        List<RubriqueBilanDto> passif,
        BigDecimal totalPassif,
        BigDecimal resultatNet,
        Instant generatedAt) {
}
