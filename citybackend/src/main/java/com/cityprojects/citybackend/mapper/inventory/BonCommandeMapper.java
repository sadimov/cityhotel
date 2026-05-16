package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonCommandeDto;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.LigneBonCommande;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct entre {@link BonCommande} / {@link LigneBonCommande} et leurs DTOs.
 *
 * <p>Le toDto du BC ne mappe pas les lignes : le service les charge via le repository
 * dedie {@code LigneBonCommandeRepository.findByBonCommandeIdOrderByLigneIdAsc} et
 * complete le DTO via {@link #withLignes(BonCommandeDto, List)}.</p>
 */
@Mapper(componentModel = "spring")
public interface BonCommandeMapper {

    @Mapping(target = "lignes", ignore = true)
    @Mapping(target = "nomFournisseur", ignore = true)
    BonCommandeDto toDto(BonCommande entity);

    @Mapping(target = "sousTotal", expression = "java(entity.getSousTotal())")
    LigneBonCommandeDto toLigneDto(LigneBonCommande entity);

    /**
     * Recompose un DTO BC avec ses lignes mappees. Pratique quand le service a
     * deja le DTO sans lignes et veut rajouter la liste apres coup.
     */
    default BonCommandeDto withLignes(BonCommandeDto dto, List<LigneBonCommandeDto> lignes) {
        return new BonCommandeDto(
                dto.bonCommandeId(),
                dto.numeroBc(),
                dto.fournisseurId(),
                dto.statut(),
                dto.dateCommande(),
                dto.dateLivraisonPrevue(),
                dto.dateLivraisonReelle(),
                dto.montantTotal(),
                dto.montantTva(),
                dto.commentaires(),
                dto.userId(),
                lignes,
                dto.createdAt(),
                dto.updatedAt(),
                dto.nomFournisseur());
    }
}
