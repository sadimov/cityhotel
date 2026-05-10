package com.cityprojects.citybackend.mapper.admin;

import com.cityprojects.citybackend.dto.admin.RoleAdminDto;
import com.cityprojects.citybackend.entity.core.Role;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct {@link Role} -&gt; {@link RoleAdminDto}.
 * <p>
 * Read-only : pas de mapping inverse car les roles sont figes par Liquibase
 * (cf. JavaDoc {@link com.cityprojects.citybackend.service.admin.RoleAdminService}).
 */
@Mapper(componentModel = "spring")
public interface RoleAdminMapper {

    RoleAdminDto toDto(Role entity);
}
