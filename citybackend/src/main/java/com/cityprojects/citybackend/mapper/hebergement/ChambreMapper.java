package com.cityprojects.citybackend.mapper.hebergement;

import com.cityprojects.citybackend.dto.hebergement.ChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Chambre} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant.</p>
 */
@Mapper(componentModel = "spring")
public interface ChambreMapper {

    @Mapping(target = "nomTypeChambre", ignore = true)
    ChambreDto toDto(Chambre entity);

    @Mapping(target = "chambreId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Chambre toEntity(ChambreCreateDto dto);
}
