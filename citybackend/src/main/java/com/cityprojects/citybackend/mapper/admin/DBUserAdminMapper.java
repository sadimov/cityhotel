package com.cityprojects.citybackend.mapper.admin;

import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link DBUser} et son DTO admin.
 *
 * <h2>Pas de toEntity / updateEntity</h2>
 * <p>La construction d'un {@link DBUser} et son update touchent au
 * {@code passwordHash}, au lien {@code hotel}, au lien {@code role} et a
 * la validation BCrypt — toutes operations metier qui ne doivent pas etre
 * deleguees a un mapper. Le service {@link
 * com.cityprojects.citybackend.service.admin.DBUserAdminService} gere
 * directement la mecanique create/update.</p>
 *
 * <p>Le mapper se contente donc d'aplatir l'entite vers le DTO de sortie,
 * en derefencant {@code hotel.*} et {@code role.*} pour les exposer a plat.</p>
 */
@Mapper(componentModel = "spring")
public interface DBUserAdminMapper {

    @Mapping(target = "nomComplet", expression = "java(entity.getNomComplet())")
    @Mapping(target = "hotelId", source = "hotel.hotelId")
    @Mapping(target = "hotelCode", source = "hotel.hotelCode")
    @Mapping(target = "hotelNom", source = "hotel.hotelNom")
    @Mapping(target = "roleId", source = "role.roleId")
    @Mapping(target = "roleCode", source = "role.roleCode")
    @Mapping(target = "roleNom", source = "role.roleNom")
    DBUserAdminDto toDto(DBUser entity);
}
