package com.cityprojects.citybackend.mapper.client;

import com.cityprojects.citybackend.dto.client.SocieteCreateDto;
import com.cityprojects.citybackend.dto.client.SocieteDto;
import com.cityprojects.citybackend.dto.client.SocieteUpdateDto;
import com.cityprojects.citybackend.entity.client.Societe;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper MapStruct entre {@link Societe} et ses DTOs.
 * <p>
 * <b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant (cf. CLAUDE.md
 * racine §10) : la valeur est populee a l'INSERT par Hibernate via le resolver
 * de tenant, jamais lue depuis le payload HTTP.
 * <p>
 * Pour {@code updateEntity}, on utilise {@link NullValuePropertyMappingStrategy#IGNORE}
 * sur le champ {@code actif} uniquement (les autres champs ecrasent la valeur),
 * ce qui correspond au contrat documente du DTO : {@code actif=null} -&gt; ne pas toucher.
 */
@Mapper(componentModel = "spring")
public interface SocieteMapper {

    /**
     * Convertit l'entite en DTO de sortie. {@code hotelId} volontairement
     * exclu (n'existe pas sur le DTO).
     */
    SocieteDto toDto(Societe entity);

    /**
     * Construit une nouvelle entite a partir du DTO de creation.
     * <p>
     * - {@code societeId} est ignore (genere par la base).<br>
     * - {@code hotelId} est ignore (gere par Hibernate via @TenantId).<br>
     * - {@code actif} est force a {@code true} cote service apres ce mapping
     *   (pas dans le DTO de creation pour eviter qu'un client cree une societe
     *   inactive d'office).<br>
     * - Champs d'audit ignores (geres par {@link com.cityprojects.citybackend.common.audit.AuditableEntity}).
     */
    @Mapping(target = "societeId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Societe toEntity(SocieteCreateDto dto);

    /**
     * Met a jour l'entite avec les champs du DTO. Les nulls n'ecrasent pas
     * (comportement {@link NullValuePropertyMappingStrategy#IGNORE}) :
     * permet de patcher partiellement (ex. ne pas toucher {@code actif}).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "societeId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget Societe target, SocieteUpdateDto dto);
}
