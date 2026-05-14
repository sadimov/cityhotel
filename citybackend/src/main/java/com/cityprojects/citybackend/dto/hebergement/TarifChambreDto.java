package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.hebergement.TarifChambre}.
 *
 * <p>Tour 44 Phase 1 : exposition lecture des tarifs saisonniers utilises par
 * le calendrier des reservations (calcul du prix d'une nuit a une date donnee).</p>
 */
public record TarifChambreDto(
        Long tarifId,
        Long typeId,
        String nomTarif,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal prixNuit,
        BigDecimal prixWeekend,
        Integer priorite,
        Boolean actif) {
}
