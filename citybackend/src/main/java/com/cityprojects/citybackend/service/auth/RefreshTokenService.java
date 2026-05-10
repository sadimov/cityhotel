package com.cityprojects.citybackend.service.auth;

import com.cityprojects.citybackend.entity.core.RefreshToken;

/**
 * Gestion des refresh tokens (Tour 38 C6/C7) :
 * <ul>
 *   <li>{@link #issue} - emet un nouveau token apres login.</li>
 *   <li>{@link #rotate} - consomme l'ancien, emet le nouveau, detecte la
 *       reutilisation (revocation cross-device si vol probable).</li>
 *   <li>{@link #revokeAllForUser} - logout / desactivation compte.</li>
 *   <li>{@link #purgeExpired} - menage scheduled.</li>
 * </ul>
 *
 * <h3>Resultat d'emission</h3>
 * <p>{@link IssuedToken} : porte a la fois le token CLAIR (a renvoyer au client
 * une seule fois) et l'entite persistee (avec id, expiresAt). Le token clair
 * n'est JAMAIS persiste — uniquement son SHA-256.</p>
 */
public interface RefreshTokenService {

    /**
     * Emet un nouveau refresh token pour {@code userId} (et eventuellement
     * {@code hotelId} pour SUPERADMIN ROOT). Le token clair retourne dans
     * {@link IssuedToken#clearToken()} doit etre transmis au client une seule
     * fois (jamais re-derivable depuis la BDD).
     */
    IssuedToken issue(Long userId, Long hotelId, String userAgent, String ipAddress);

    /**
     * Rotation : valide l'ancien token, le marque revoked, emet un nouveau.
     *
     * <p>Detection de reutilisation : si l'ancien token est deja revoked, on
     * revoque TOUS les refresh tokens de ce user (vol probable) et on leve
     * une exception.</p>
     */
    IssuedToken rotate(String oldClearToken, String userAgent, String ipAddress);

    /**
     * Revoque tous les refresh tokens d'un user (logout cross-device, vol detecte,
     * desactivation compte).
     */
    int revokeAllForUser(Long userId);

    /**
     * Purge les tokens expires (scheduled job).
     */
    int purgeExpired();

    /**
     * Wrapper sur le couple (token clair, entite persistee).
     */
    record IssuedToken(String clearToken, RefreshToken entity) {
    }
}
