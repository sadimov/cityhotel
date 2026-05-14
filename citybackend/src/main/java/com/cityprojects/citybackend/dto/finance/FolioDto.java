package com.cityprojects.citybackend.dto.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de sortie (Tour 46) — folio du compte auxiliaire client sur une plage
 * de dates donnee.
 *
 * <p>Vue "extrait de compte" filtree par dates, exposee au frontend dans la
 * modale paiement (Tour 46). Calculs :
 * <ul>
 *   <li>{@code soldeOuverture} : somme algebrique des operations
 *       <em>anterieures</em> a {@code dateDebut} (CREDIT positif si encaisses
 *       d'avance, DEBIT positif si dette). Convention : positif = dette client.</li>
 *   <li>{@code totalDebits} / {@code totalCredits} : agreges des operations
 *       <em>incluses</em> dans la plage [dateDebut, dateFin].</li>
 *   <li>{@code soldeCloture} = {@code soldeOuverture + totalDebits - totalCredits}.</li>
 * </ul>
 * Le frontend peut aussi recalculer ces totaux a partir de {@code operations}
 * pour controle de coherence.</p>
 *
 * <p>Multi-tenant : isolation Hibernate {@code @TenantId} sur Compte +
 * OperationCompte. Aucun {@code hotelId} dans le payload : le tenant est
 * resolu via JWT par le {@code @RequireTenant} du service.</p>
 */
public record FolioDto(
        Long compteId,
        Long clientId,
        String clientNom,
        BigDecimal soldeOuverture,
        BigDecimal soldeCloture,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        List<OperationCompteFolioDto> operations) {
}
