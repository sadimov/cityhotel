package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-HEB-001 — Occupation chambres sur une periode.
 *
 * <p>Calcule a la volee depuis {@code hebergement.nuitees} (taux = nuitees consommees ou
 * facturees / nuitees-chambres disponibles sur la periode).</p>
 *
 * @param from              borne inclusive
 * @param to                borne exclusive
 * @param totalChambres     nombre total de chambres actives du tenant
 * @param totalNuiteesDispo capacite theorique sur la periode (totalChambres * nbJours)
 * @param totalNuiteesOccupees nuitees CONSOMMEE + FACTUREE
 * @param tauxOccupationGlobal 0..100 (BigDecimal 2 decimales)
 * @param breakdownParType decoupage par type de chambre
 */
public record OccupationDto(
        LocalDate from,
        LocalDate to,
        Integer totalChambres,
        Long totalNuiteesDispo,
        Long totalNuiteesOccupees,
        BigDecimal tauxOccupationGlobal,
        List<TypeChambreOccupation> breakdownParType
) {

    /**
     * Detail occupation pour un type de chambre.
     *
     * @param typeId         FK type_chambre
     * @param typeCode       code metier (STD, DLX, ...)
     * @param typeNom        libelle long
     * @param nbChambres     nombre de chambres actives de ce type
     * @param nuiteesDispo   capacite theorique = nbChambres * nbJours
     * @param nuiteesOccupees nuitees consommees ou facturees
     * @param tauxOccupation 0..100
     */
    public record TypeChambreOccupation(
            Long typeId,
            String typeCode,
            String typeNom,
            Integer nbChambres,
            Long nuiteesDispo,
            Long nuiteesOccupees,
            BigDecimal tauxOccupation
    ) {
    }
}
