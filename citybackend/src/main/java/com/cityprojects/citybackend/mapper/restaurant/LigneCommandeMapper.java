package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.LigneCommandeDto;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct entre {@link LigneCommande} et son DTO de sortie (Tour 24).
 *
 * <p>Pas de mapping {@code toEntity} depuis un DTO entrant : la creation est
 * pilotee par le service ({@code CommandeServiceImpl.creerLigne()}) qui
 * snapshote {@code libelle}/{@code prixUnitaire} depuis l'article.</p>
 */
@Mapper(componentModel = "spring")
public interface LigneCommandeMapper {

    LigneCommandeDto toDto(LigneCommande entity);
}
