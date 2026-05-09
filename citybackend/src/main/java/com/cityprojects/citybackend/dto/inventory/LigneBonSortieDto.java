package com.cityprojects.citybackend.dto.inventory;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.LigneBonSortie}.
 */
public record LigneBonSortieDto(
        Long ligneId,
        Long bonSortieId,
        Long produitId,
        Integer quantiteDemandee,
        Integer quantiteServie,
        String commentaires) {
}
