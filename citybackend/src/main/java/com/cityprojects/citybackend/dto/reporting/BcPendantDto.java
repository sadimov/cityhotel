package com.cityprojects.citybackend.dto.reporting;

import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rapport R-INV-003a — Bon de commande pendant (en attente de reception complete).
 *
 * <p>Statut != RECU_COMPLET et != ANNULE.</p>
 */
public record BcPendantDto(
        Long bonCommandeId,
        String numeroBc,
        Long fournisseurId,
        StatutBonCommande statut,
        LocalDate dateCommande,
        LocalDate dateLivraisonPrevue,
        Integer ageJours,
        BigDecimal montantTotal
) {
}
