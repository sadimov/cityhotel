package com.cityprojects.citybackend.dto.admin;

/**
 * Reponse au reset password : contient le mot de passe temporaire generee
 * <b>en clair</b>, retourne <i>une seule fois</i> a l'admin appelant pour
 * communication a l'utilisateur final (par canal hors-bande).
 *
 * <p><b>NE PAS LOGGER</b> ce DTO. Le hash BCrypt est persiste cote serveur ;
 * le mot de passe en clair n'est plus jamais retournable apres cet appel.</p>
 *
 * <p>Convention metier : l'utilisateur DOIT changer son mot de passe a la
 * prochaine connexion (logique a porter cote front au tour profil/login).</p>
 */
public record DBUserResetPasswordResponseDto(
        Long userId,
        String username,
        String temporaryPassword) {
}
