package com.cityprojects.citybackend.mapper.hebergement;

import com.cityprojects.citybackend.dto.hebergement.TarifChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreDto;
import com.cityprojects.citybackend.entity.hebergement.TarifChambre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link TarifChambre} et ses DTOs.
 *
 * <p><b>Ne mappe jamais</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). Audit fields ignores en INSERT (geres par
 * {@code AuditingEntityListener}).</p>
 */
@Mapper(componentModel = "spring")
public interface TarifChambreMapper {

    TarifChambreDto toDto(TarifChambre entity);

    @Mapping(target = "tarifId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    TarifChambre toEntity(TarifChambreCreateDto dto);

    @Mapping(target = "tarifId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget TarifChambre entity, TarifChambreCreateDto dto);
}
