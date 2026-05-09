package com.cityprojects.citybackend.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.LigneBonCommande}.
 */
public record LigneBonCommandeDto(
        Long ligneId,
        Long bonCommandeId,
        Long produitId,
        Integer quantiteCommandee,
        Integer quantiteRecue,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        LocalDate dateReception) {
}
