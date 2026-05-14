package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.TypeServiceHotelierDto;
import com.cityprojects.citybackend.entity.inventory.TypeServiceHotelier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link TypeServiceHotelier} et ses DTOs.
 */
@Mapper(componentModel = "spring")
public interface TypeServiceHotelierMapper {

    TypeServiceHotelierDto toDto(TypeServiceHotelier entity);

    @Mapping(target = "typeServiceId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    TypeServiceHotelier toEntity(TypeServiceHotelierCreateDto dto);
}
