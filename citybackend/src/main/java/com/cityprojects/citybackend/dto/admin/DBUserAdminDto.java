package com.cityprojects.citybackend.dto.admin;

import java.time.LocalDateTime;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.core.DBUser}
 * cote administration SUPERADMIN.
 * <p>
 * <b>NE CONTIENT JAMAIS</b> le {@code passwordHash} (le hash BCrypt n'est
 * pas confidentiel mais sa fuite reduit l'effort attaquant : on ne le
 * communique jamais en API). Les vues publiques utilisent
 * {@link com.cityprojects.citybackend.security.UserPrincipal}.
 * <p>
 * Inclut {@code hotelId} et {@code hotelCode} : le SUPERADMIN voit
 * cross-tenant et doit savoir a quel hotel rattacher chaque user.
 */
public record DBUserAdminDto(
        Long userId,
        String username,
        String email,
        String prenom,
        String nom,
        String nomComplet,
        String telephone,
        String poste,
        Boolean actif,
        LocalDateTime derniereConnexion,
        Integer tentativesConnexion,
        Boolean compteVerrouille,
        LocalDateTime dateCreation,
        LocalDateTime dateModification,
        Long hotelId,
        String hotelCode,
        String hotelNom,
        Integer roleId,
        String roleCode,
        String roleNom) {
}
