package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.entity.inventory.ServiceHotelier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link ServiceHotelier} et ses DTOs.
 */
@Mapper(componentModel = "spring")
public interface ServiceHotelierMapper {

    ServiceHotelierDto toDto(ServiceHotelier entity);

    @Mapping(target = "serviceId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ServiceHotelier toEntity(ServiceHotelierCreateDto dto);
}
