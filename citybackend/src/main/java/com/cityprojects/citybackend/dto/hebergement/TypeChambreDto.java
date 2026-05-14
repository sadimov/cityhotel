package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.CategorieEspace;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.hebergement.TypeChambre}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 *
 * <p>Tour 49 : ajout {@code categorie} (CHAMBRE par defaut, SALLE pour les
 * salles de conferences/reunions reservees a la journee).</p>
 */
public record TypeChambreDto(
        Long typeId,
        String typeCode,
        String typeNom,
        String description,
        BigDecimal superficie,
        Integer nbLitsMax,
        Integer nbPersonnesMax,
        BigDecimal prixBase,
        CategorieEspace categorie,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
