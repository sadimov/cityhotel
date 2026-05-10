package com.cityprojects.citybackend.common.security;

import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitaires partages pour extraire l'identite courante du
 * {@link SecurityContextHolder}.
 *
 * <p>Centralise le pattern duplique dans plusieurs services :
 * {@code SecurityContextHolder.getContext().getAuthentication()} suivi d'un
 * test {@code instanceof UserPrincipal}. Permet d'eviter les divergences
 * subtiles (cle d'erreur i18n differente d'un service a l'autre, traitement
 * de {@code null} different).</p>
 *
 * <p><b>Tour 40bis (refactor H7)</b> : extrait depuis {@code ReservationServiceImpl},
 * {@code TicketServiceImpl} et autres impl services qui dupliquent ce pattern.
 * Comportement strictement preserve : meme cle d'erreur i18n
 * {@code error.user.unknown} (cf. doctrine MessagesService).</p>
 *
 * <p><b>Classe utility</b> : final + constructeur prive. Methodes statiques
 * pures (pas d'etat).</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // utility class
    }

    /**
     * Retourne l'identifiant utilisateur courant depuis le {@link SecurityContextHolder},
     * ou {@code null} si aucun {@link UserPrincipal} n'est present (cas d'un appel
     * depuis un job programme ou un test sans setup security).
     *
     * <p>Ne leve jamais d'exception : convient aux flows ou l'identite est
     * facultative (ex: trace d'audit best-effort).</p>
     */
    public static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    /**
     * Retourne l'identifiant utilisateur courant depuis le {@link SecurityContextHolder},
     * ou leve une {@link BusinessException} (cle i18n {@code error.user.unknown})
     * si aucun {@link UserPrincipal} n'est present.
     *
     * <p>Convient aux flows metier qui exigent l'identite du createur (creation
     * facture, paiement, reservation...). Le {@code GlobalExceptionHandler}
     * traduit en HTTP 4xx propre.</p>
     */
    public static Long currentUserIdOrThrow() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException("error.user.unknown");
        }
        return userId;
    }
}
