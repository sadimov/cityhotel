package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-FIN-002 — Encours clients (factures emises non totalement payees).
 *
 * <p>Aging buckets : 0-30, 30-60, 60-90, 90+ jours depuis {@code dateFacture}.
 * Calcul a la date de reference (par defaut {@code LocalDate.now()}).</p>
 *
 * @param reference     date de calcul des buckets
 * @param totalEncours  somme {@code (montantTtc - montantPaye)} pour toutes les
 *                      factures non soldees (statut != PAYEE et != ANNULEE)
 * @param bucket0_30    encours <= 30 jours
 * @param bucket30_60   encours ]30, 60]
 * @param bucket60_90   encours ]60, 90]
 * @param bucket90Plus  encours > 90
 * @param lignes        detail par facture
 */
public record EncoursClientDto(
        LocalDate reference,
        BigDecimal totalEncours,
        BigDecimal bucket0_30,
        BigDecimal bucket30_60,
        BigDecimal bucket60_90,
        BigDecimal bucket90Plus,
        List<EncoursLigneDto> lignes
) {

    /**
     * Ligne d'encours pour une facture donnee.
     */
    public record EncoursLigneDto(
            Long factureId,
            String numeroFacture,
            LocalDate dateFacture,
            LocalDate dateEcheance,
            Long clientId,
            BigDecimal montantTtc,
            BigDecimal montantPaye,
            BigDecimal montantDu,
            int ageJours,
            String bucket
    ) {
    }
}
