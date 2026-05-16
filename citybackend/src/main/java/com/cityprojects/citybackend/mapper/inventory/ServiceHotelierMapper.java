package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.ServiceHotelierCreateDto;
import com.cityprojects.citybackend.dto.inventory.ServiceHotelierDto;
import com.cityprojects.citybackend.entity.inventory.ServiceHotelier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link ServiceHotelier} et ses DTOs.
 *
 * <p>Tour 55b : le contrat DTO expose {@code codeService}/{@code nomService}/
 * {@code uniteMesure} (cf. frontend) alors que les colonnes BD restent
 * {@code code}/{@code nom}/{@code unite}. Les mappings explicites assurent la
 * traduction.</p>
 */
@Mapper(componentModel = "spring")
public interface ServiceHotelierMapper {

    @Mapping(source = "code", target = "codeService")
    @Mapping(source = "nom", target = "nomService")
    @Mapping(source = "unite", target = "uniteMesure")
    ServiceHotelierDto toDto(ServiceHotelier entity);

    @Mapping(target = "serviceId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(source = "codeService", target = "code")
    @Mapping(source = "nomService", target = "nom")
    @Mapping(source = "uniteMesure", target = "unite")
    ServiceHotelier toEntity(ServiceHotelierCreateDto dto);
}
