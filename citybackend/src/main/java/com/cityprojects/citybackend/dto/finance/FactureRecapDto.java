package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.StatutFacture;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vue tronquee d'une facture pour le recap paiements d'une reservation
 * (Tour 44 Phase 1). Ne contient que ce dont l'onglet "Paiements" du
 * calendrier a besoin (pas de lignes detaillees).
 */
public record FactureRecapDto(
        Long factureId,
        String numero,
        StatutFacture statut,
        LocalDate dateFacture,
        BigDecimal montantTotal,
        BigDecimal montantPaye,
        BigDecimal reste) {
}
