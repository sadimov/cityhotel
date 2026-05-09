package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.menage.Tache;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Tache} et ses DTOs.
 *
 * <p>{@code hotelId} jamais mappe depuis le DTO. Les heures reelles
 * ({@code heureDebutReelle}, {@code heureFinReelle}) sont posees par le service
 * lors des transitions {@code commencer()}/{@code terminer()}, pas via le
 * DTO.</p>
 */
@Mapper(componentModel = "spring")
public interface TacheMapper {

    TacheDto toDto(Tache entity);

    @Mapping(target = "tacheId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "heureDebutReelle", ignore = true)
    @Mapping(target = "heureFinReelle", ignore = true)
    @Mapping(target = "problemesDetectes", ignore = true)
    @Mapping(target = "materielUtilise", source = "materielNecessaire")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true) // Tour 30 etape 3 : @Version, gere par Hibernate
    Tache toEntity(TacheCreateDto dto);
}
