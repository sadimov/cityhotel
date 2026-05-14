package com.cityprojects.citybackend.dto.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recap complet des factures et paiements rattaches a une reservation
 * (Tour 44 Phase 1). Sert l'onglet "Paiements" du calendrier des reservations.
 *
 * <p>Tous les montants sont exprimes dans la devise des factures (MRU sauf
 * exception) - le calcul de conversion multi-devises est hors-scope Phase 1.</p>
 */
public record RecapPaiementsReservationDto(
        Long reservationId,
        List<FactureRecapDto> factures,
        List<PaiementRecapDto> paiements,
        BigDecimal totalGlobal,
        BigDecimal payeGlobal,
        BigDecimal resteGlobal) {
}
