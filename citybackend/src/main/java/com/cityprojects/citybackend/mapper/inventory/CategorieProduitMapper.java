package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitDto;
import com.cityprojects.citybackend.entity.inventory.CategorieProduit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link CategorieProduit} et ses DTOs.
 */
@Mapper(componentModel = "spring")
public interface CategorieProduitMapper {

    CategorieProduitDto toDto(CategorieProduit entity);

    @Mapping(target = "categorieId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    CategorieProduit toEntity(CategorieProduitCreateDto dto);
}
