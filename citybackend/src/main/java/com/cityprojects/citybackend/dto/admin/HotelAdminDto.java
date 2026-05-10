package com.cityprojects.citybackend.dto.admin;

import java.time.LocalDateTime;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.core.Hotel}
 * cote administration SUPERADMIN.
 * <p>
 * Expose la totalite des informations de configuration d'un hotel (le SUPERADMIN
 * a un besoin legitime cross-tenant de tout voir). Les controllers metier
 * normaux n'utilisent <b>pas</b> ce DTO : ils ne doivent jamais retourner un
 * objet hotel directement.
 */
public record HotelAdminDto(
        Long hotelId,
        String hotelCode,
        String hotelNom,
        String hotelAdresse,
        String hotelTel,
        String logoUrl,
        String ville,
        String pays,
        String boitePostale,
        String email,
        String siteWeb,
        String devise,
        String codePays,
        String fuseauHoraire,
        Boolean actif,
        LocalDateTime dateCreation,
        LocalDateTime dateModification) {
}
