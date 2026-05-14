package com.cityprojects.citybackend.dto.reporting;

import com.cityprojects.citybackend.entity.finance.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-RES-001 — Journal de caisse du jour (Tour 41 P2).
 *
 * <p>Source : {@code restaurant.commandes} encaissees ce jour (factureId non null
 * OU mode reglement COMPTANT) + paiements POS du jour groupes par mode.</p>
 *
 * @param date              date du journal
 * @param nbCommandes       nombre de commandes encaissees
 * @param totalRecettes     somme {@code montantTtc} encaisses (= montantPaye sur les commandes)
 * @param breakdownModes    repartition par mode paiement (depuis Paiement.modePaiement)
 */
public record JournalCaisseDto(
        LocalDate date,
        Long nbCommandes,
        BigDecimal totalRecettes,
        List<ModePaiementLigneDto> breakdownModes
) {

    /** Ligne par mode de paiement. */
    public record ModePaiementLigneDto(
            ModePaiement modePaiement,
            Long nbPaiements,
            BigDecimal montantTotal
    ) {
    }
}
