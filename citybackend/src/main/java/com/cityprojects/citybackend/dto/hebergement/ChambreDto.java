package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.StatutChambre;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.hebergement.Chambre}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 */
public record ChambreDto(
        Long chambreId,
        String numeroChambre,
        Long typeId,
        Integer etage,
        StatutChambre statut,
        Integer nbLits,
        Integer nbPersonnesMax,
        String equipements,
        String description,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt,
        /** Nom du type de chambre (résolu côté service, anti-N+1). */
        String nomTypeChambre) {

    public ChambreDto withResolvedNames(String nomType) {
        return new ChambreDto(
                chambreId, numeroChambre, typeId, etage, statut, nbLits, nbPersonnesMax,
                equipements, description, actif, createdAt, updatedAt, nomType);
    }
}
