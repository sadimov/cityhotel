package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vue tronquee d'un paiement pour le recap d'une reservation
 * (Tour 44 Phase 1). Le montant retenu est le montant de l'affectation a la
 * facture concernee, pas le {@code montantTotal} du paiement.
 */
public record PaiementRecapDto(
        Long paiementId,
        String numeroPaiement,
        LocalDate datePaiement,
        ModePaiement modePaiement,
        StatutPaiement statut,
        BigDecimal montantAffecte,
        Long factureId,
        String factureNumero) {
}
