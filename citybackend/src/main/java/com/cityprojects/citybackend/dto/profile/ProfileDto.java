package com.cityprojects.citybackend.dto.profile;

import java.time.LocalDateTime;

/**
 * DTO de sortie pour le profil self-service de l'utilisateur courant.
 *
 * <p>Vue restreinte par rapport a {@link com.cityprojects.citybackend.dto.admin.DBUserAdminDto} :
 * <ul>
 *   <li>Aucune information sensible (passwordHash exclu, tentativesConnexion exclu).</li>
 *   <li>{@code hotelId} et {@code roleId} omis : un user n'a pas a connaitre ces ids
 *       techniques sur sa propre fiche (le front affiche {@code hotelNom} / {@code roleNom}).</li>
 *   <li>{@code avatarUrl} relatif (ex. {@code /uploads/avatars/user-42-uuid.jpg})
 *       ou {@code null} si pas d'avatar.</li>
 *   <li>{@code motPasseTemporaire} expose pour forcer le change-password au premier login.</li>
 * </ul>
 */
public record ProfileDto(
        Long userId,
        String username,
        String email,
        String prenom,
        String nom,
        String nomComplet,
        String telephone,
        String poste,
        String hotelNom,
        String roleCode,
        String roleNom,
        String avatarUrl,
        LocalDateTime derniereConnexion,
        Boolean motPasseTemporaire) {
}
