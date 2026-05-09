package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ArticleMenuCreateDto;
import com.cityprojects.citybackend.dto.restaurant.ArticleMenuDto;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link ArticleMenu} et ses DTOs.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (resolver
 * Hibernate). Le statut initial ({@code ACTIF}) et l'audit sont positionnes
 * par le service / les listeners JPA.</p>
 */
@Mapper(componentModel = "spring")
public interface ArticleMenuMapper {

    ArticleMenuDto toDto(ArticleMenu entity);

    @Mapping(target = "articleId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ArticleMenu toEntity(ArticleMenuCreateDto dto);
}
