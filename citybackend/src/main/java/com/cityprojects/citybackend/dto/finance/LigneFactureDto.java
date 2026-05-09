package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.finance.LigneFacture}.
 */
public record LigneFactureDto(
        Long ligneFactureId,
        Long factureId,
        TypeLigneFacture typeLigne,
        Long nuiteeId,
        Long produitId,
        Long commandeId,
        Long serviceId,
        String libelle,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        BigDecimal tauxTva,
        BigDecimal montantHt,
        BigDecimal montantTva,
        BigDecimal montantTtc,
        LocalDate datePrestation) {
}
