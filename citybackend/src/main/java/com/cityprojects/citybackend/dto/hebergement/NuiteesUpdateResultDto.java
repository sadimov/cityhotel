package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;

/**
 * DTO de sortie (Tour 45) : resultat agrege d'une mise a jour en lot des
 * montants de nuitees ({@code PATCH /api/hebergement/nuitees/montants}).
 *
 * <p>{@code updatedCount} : nombre de nuitees effectivement modifiees (les
 * demandes filtrees pour cause de facture parente terminale ne sont PAS
 * comptees - mais le service leve une {@code BusinessException} pour le
 * client le premier blocage rencontre, donc en pratique
 * {@code updatedCount = nombre de demandes} en cas de succes total).</p>
 *
 * <p>{@code totalImpact} : somme algebrique des deltas (nouveau - ancien
 * prix). Positif si le total a augmente, negatif sinon.</p>
 */
public record NuiteesUpdateResultDto(
        int updatedCount,
        BigDecimal totalImpact) {
}
