package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonSortieDto;
import com.cityprojects.citybackend.entity.inventory.BonSortie;
import com.cityprojects.citybackend.entity.inventory.LigneBonSortie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct entre {@link BonSortie} / {@link LigneBonSortie} et leurs DTOs.
 */
@Mapper(componentModel = "spring")
public interface BonSortieMapper {

    @Mapping(target = "lignes", ignore = true)
    BonSortieDto toDto(BonSortie entity);

    @Mapping(target = "nomProduit", ignore = true)
    @Mapping(target = "codeProduit", ignore = true)
    @Mapping(target = "uniteMesure", ignore = true)
    LigneBonSortieDto toLigneDto(LigneBonSortie entity);

    default BonSortieDto withLignes(BonSortieDto dto, List<LigneBonSortieDto> lignes) {
        return new BonSortieDto(
                dto.bonSortieId(),
                dto.numeroBs(),
                dto.destination(),
                dto.statut(),
                dto.dateSortie(),
                dto.commentaires(),
                dto.motifAnnulation(),
                dto.userId(),
                lignes,
                dto.createdAt(),
                dto.updatedAt());
    }
}
