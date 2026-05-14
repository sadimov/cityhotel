package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-INV-003 — Bons de commande pendants + rotation produits (Tour 41 P2).
 */
public interface BcPendantsRotationReportService {

    /** Liste des BC pendants (statut != RECU_COMPLET ET != ANNULE). */
    List<BcPendantDto> findBcPendants();

    /** Calcule la rotation produit sur la plage [from, to). */
    List<RotationProduitDto> computeRotation(LocalDate from, LocalDate to);

    byte[] exportBcPendantsXlsx();

    byte[] exportRotationXlsx(LocalDate from, LocalDate to);
}
