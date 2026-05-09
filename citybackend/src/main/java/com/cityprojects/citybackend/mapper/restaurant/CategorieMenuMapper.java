package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CategorieMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CategorieMenuDto;
import com.cityprojects.citybackend.entity.restaurant.CategorieMenu;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link CategorieMenu} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). Audit (createdAt/updatedAt/createdBy/updatedBy) ignore en
 * INSERT (gere par {@code AuditingEntityListener}).</p>
 */
@Mapper(componentModel = "spring")
public interface CategorieMenuMapper {

    CategorieMenuDto toDto(CategorieMenu entity);

    @Mapping(target = "categorieId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    CategorieMenu toEntity(CategorieMenuCreateDto dto);
}
