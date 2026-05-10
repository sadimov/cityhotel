package com.cityprojects.citybackend.mapper.admin;

import com.cityprojects.citybackend.dto.admin.HotelAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper MapStruct entre {@link Hotel} et ses DTOs admin.
 *
 * <p>{@code hotelId}, {@code dateCreation}, {@code dateModification} et la
 * collection {@code users} ne sont jamais mappes depuis un DTO entrant
 * (genere par la base ou maintenu par {@code AuditingEntityListener}).</p>
 *
 * <p>{@code hotelCode} est mappe par {@link #toEntity(HotelCreateAdminDto)} mais
 * <b>ignore</b> par {@link #updateEntity(Hotel, HotelUpdateAdminDto)} : la cle
 * metier est immuable apres creation (cf. {@link HotelUpdateAdminDto}).</p>
 *
 * <p>{@code actif} : non mappe depuis l'update DTO (les endpoints dedies
 * desactiver/reactiver gerent ce champ).</p>
 */
@Mapper(componentModel = "spring")
public interface HotelAdminMapper {

    HotelAdminDto toDto(Hotel entity);

    /**
     * Cree une entite Hotel a partir du DTO de creation.
     * <p>
     * Champs ignores : id, audit, collection {@code users} (toujours vide a
     * la creation, alimentee via {@link com.cityprojects.citybackend.entity.core.DBUser#setHotel(Hotel)}).
     */
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateModification", ignore = true)
    @Mapping(target = "users", ignore = true)
    Hotel toEntity(HotelCreateAdminDto dto);

    /**
     * Met a jour l'entite a partir du DTO. Les champs {@code null} ne sont
     * pas appliques (semantique PATCH).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "hotelCode", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateModification", ignore = true)
    @Mapping(target = "users", ignore = true)
    void updateEntity(@MappingTarget Hotel target, HotelUpdateAdminDto dto);
}
