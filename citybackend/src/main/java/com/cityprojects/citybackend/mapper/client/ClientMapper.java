package com.cityprojects.citybackend.mapper.client;

import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.client.ClientUpdateDto;
import com.cityprojects.citybackend.entity.client.Client;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper MapStruct entre {@link Client} et ses DTOs.
 * <p>
 * <b>NE MAPPE JAMAIS</b> {@code hotelId} depuis un DTO entrant.
 * <p>
 * {@code numeroClient} est genere cote service (NumerotationService) et NON
 * mappable via update : il reste IMMUABLE apres creation.
 */
@Mapper(componentModel = "spring")
public interface ClientMapper {

    /**
     * Convertit l'entite en DTO. {@code nomComplet} est calcule par
     * {@link Client#getNomComplet()} (MapStruct l'appelle automatiquement
     * si le getter existe sur la source).
     */
    ClientDto toDto(Client entity);

    /**
     * Cree une entite a partir du DTO de creation.
     * <p>
     * - {@code clientId} : ignore (genere base).<br>
     * - {@code hotelId} : ignore (resolver Hibernate).<br>
     * - {@code numeroClient} : ignore (genere par le service via NumerotationService).<br>
     * - {@code actif} : ignore ici, force a {@code true} cote service.<br>
     * - audit : ignore.
     */
    @Mapping(target = "clientId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "numeroClient", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Client toEntity(ClientCreateDto dto);

    /**
     * Met a jour l'entite a partir du DTO. {@code numeroClient} reste
     * IMMUABLE (genere une seule fois). Champ {@code actif} : null = ne pas toucher.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "clientId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "numeroClient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(@MappingTarget Client target, ClientUpdateDto dto);
}
