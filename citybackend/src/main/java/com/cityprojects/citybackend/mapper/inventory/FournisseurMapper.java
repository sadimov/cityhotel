package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurDto;
import com.cityprojects.citybackend.entity.inventory.Fournisseur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Fournisseur} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant.</p>
 */
@Mapper(componentModel = "spring")
public interface FournisseurMapper {

    FournisseurDto toDto(Fournisseur entity);

    @Mapping(target = "fournisseurId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Fournisseur toEntity(FournisseurCreateDto dto);
}
