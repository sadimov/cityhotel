package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.PlanningCreateDto;
import com.cityprojects.citybackend.dto.menage.PlanningDto;
import com.cityprojects.citybackend.entity.menage.Planning;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Planning} et ses DTOs.
 *
 * <p>{@code hotelId} jamais mappe depuis le DTO.</p>
 */
@Mapper(componentModel = "spring")
public interface PlanningMapper {

    PlanningDto toDto(Planning entity);

    @Mapping(target = "planningId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Planning toEntity(PlanningCreateDto dto);
}
