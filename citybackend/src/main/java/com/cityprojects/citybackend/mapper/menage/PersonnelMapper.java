package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.entity.menage.Personnel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Personnel} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). Les colonnes d'audit sont aussi ignorees a l'INSERT (gerees par
 * {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}).</p>
 */
@Mapper(componentModel = "spring")
public interface PersonnelMapper {

    @Mapping(target = "nomComplet", expression = "java(entity.getNomComplet())")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    PersonnelDto toDto(Personnel entity);

    @Mapping(target = "personnelId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Personnel toEntity(PersonnelCreateDto dto);
}
