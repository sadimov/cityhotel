package com.cityprojects.citybackend.mapper.hebergement;

import com.cityprojects.citybackend.dto.hebergement.TypeChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TypeChambreDto;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link TypeChambre} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). Audit (createdAt/updatedAt/createdBy/updatedBy) ignore en
 * INSERT (gere par {@code AuditingEntityListener}).</p>
 */
@Mapper(componentModel = "spring")
public interface TypeChambreMapper {

    TypeChambreDto toDto(TypeChambre entity);

    @Mapping(target = "typeId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    TypeChambre toEntity(TypeChambreCreateDto dto);
}
