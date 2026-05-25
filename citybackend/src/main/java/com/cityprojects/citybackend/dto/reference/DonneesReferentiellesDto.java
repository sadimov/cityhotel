package com.cityprojects.citybackend.dto.reference;

/**
 * DTO de sortie d'une entrée du référentiel global. Aligné sur le modèle
 * frontend {@code DonneesReferentielles} (cityfrontend/src/app/features/clients/models/client.model.ts).
 */
public record DonneesReferentiellesDto(
        Long refId,
        String categorie,
        String code,
        String libelle,
        String libelleEn,
        String libelleAr,
        Integer ordreAffichage,
        Boolean actif) {
}
