package com.cityprojects.citybackend.mapper.admin;

import com.cityprojects.citybackend.dto.admin.ParametreAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.ParametreUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.Parametre;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper MapStruct {@link Parametre} &lt;-&gt; DTOs admin.
 *
 * <p>{@code modifiable} ignore depuis les DTOs entrants : il est force par le
 * service ({@code true} a la creation, jamais modifiable via l'API). Seul un
 * changeset Liquibase peut produire un parametre {@code modifiable=false}.</p>
 *
 * <p>{@code cle} ignore par updateEntity : immuable apres creation.</p>
 */
@Mapper(componentModel = "spring")
public interface ParametreAdminMapper {

    ParametreAdminDto toDto(Parametre entity);

    @Mapping(target = "parametreId", ignore = true)
    @Mapping(target = "modifiable", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Parametre toEntity(ParametreCreateAdminDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "parametreId", ignore = true)
    @Mapping(target = "cle", ignore = true)
    @Mapping(target = "modifiable", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget Parametre target, ParametreUpdateAdminDto dto);
}
