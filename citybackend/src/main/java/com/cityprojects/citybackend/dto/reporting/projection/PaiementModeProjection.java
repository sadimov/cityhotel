package com.cityprojects.citybackend.dto.reporting.projection;

import com.cityprojects.citybackend.entity.finance.ModePaiement;

import java.math.BigDecimal;

/**
 * Projection JPQL : paiements groupes par mode sur un jour (R-RES-001).
 */
public interface PaiementModeProjection {

    ModePaiement getModePaiement();

    Long getNbPaiements();

    BigDecimal getMontantTotal();
}
