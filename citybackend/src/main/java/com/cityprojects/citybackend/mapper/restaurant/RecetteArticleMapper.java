package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.RecetteArticleCreateDto;
import com.cityprojects.citybackend.dto.restaurant.RecetteArticleDto;
import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link RecetteArticle} et ses DTOs (Tour 25).
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). L'audit est positionne par les listeners JPA, le statut
 * {@code actif} par defaut (TRUE).</p>
 */
@Mapper(componentModel = "spring")
public interface RecetteArticleMapper {

    RecetteArticleDto toDto(RecetteArticle entity);

    @Mapping(target = "recetteId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    RecetteArticle toEntity(RecetteArticleCreateDto dto);
}
