package com.cityprojects.citybackend.mapper.profile;

import com.cityprojects.citybackend.dto.profile.ProfileDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct {@link DBUser} -&gt; {@link ProfileDto}.
 *
 * <p>Le mapper se contente d'aplatir l'entite (hotel.hotelNom, role.roleCode/roleNom)
 * et d'exposer {@code nomComplet}. Pas de {@code toEntity} : le service
 * {@link com.cityprojects.citybackend.service.profile.ProfileService} gere
 * directement les updates en patch (immuables exclus, cf. {@link
 * com.cityprojects.citybackend.dto.profile.ProfileUpdateDto}).
 *
 * <p>{@code avatarUrl} n'est PAS calcule ici : c'est le service qui assemble
 * le chemin public (prefixe {@code /uploads/avatars/}) car le mapper ne doit
 * pas connaitre la config Spring (cf. {@link com.cityprojects.citybackend.config.WebConfig}).
 */
@Mapper(componentModel = "spring")
public interface ProfileMapper {

    @Mapping(target = "nomComplet", expression = "java(entity.getNomComplet())")
    @Mapping(target = "hotelNom", source = "hotel.hotelNom")
    @Mapping(target = "roleCode", source = "role.roleCode")
    @Mapping(target = "roleNom", source = "role.roleNom")
    @Mapping(target = "avatarUrl", ignore = true) // injecte par le service via withAvatarUrl(...)
    ProfileDto toDto(DBUser entity);

    /**
     * Reconstruit un {@link ProfileDto} avec l'{@code avatarUrl} positionne.
     * Utilise par le service pour ajouter l'URL publique sans dupliquer la
     * logique de flatten.
     */
    default ProfileDto withAvatarUrl(ProfileDto base, String avatarUrl) {
        return new ProfileDto(
                base.userId(), base.username(), base.email(),
                base.prenom(), base.nom(), base.nomComplet(),
                base.telephone(), base.poste(),
                base.hotelNom(), base.roleCode(), base.roleNom(),
                avatarUrl,
                base.derniereConnexion(), base.motPasseTemporaire());
    }
}
