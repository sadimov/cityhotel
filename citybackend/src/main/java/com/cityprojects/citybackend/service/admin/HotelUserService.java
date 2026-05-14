package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserResetPasswordResponseDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service de gestion des utilisateurs <b>par un ADMIN d'hotel</b>, dans le
 * perimetre de son tenant courant.
 *
 * <h2>Difference avec {@link DBUserAdminService} (SUPERADMIN)</h2>
 * <ul>
 *   <li>Le tenant courant est resolu via {@code TenantContext.get()} (alimente
 *       par le JWT) — jamais en path-param. L'ADMIN n'a aucun moyen de viser
 *       un autre hotel via cet API.</li>
 *   <li>Garde anti-escalation : un ADMIN ne peut creer ni promouvoir un user
 *       avec un role {@code SUPERADMIN} ou {@code ADMIN} (refus
 *       {@code error.user.role.escalation.forbidden}).</li>
 *   <li>Garde anti-suicide : un ADMIN ne peut ni se desactiver, ni se
 *       verrouiller, ni reset son propre mot de passe, ni modifier sa propre
 *       fiche via cet API (utiliser {@code /api/profile/me/...} a la place).
 *       Refus {@code error.user.self.action.forbidden}.</li>
 * </ul>
 *
 * <h2>Reutilisation DTOs</h2>
 * <p>Les DTOs {@code DBUser*AdminDto} sont reutilises tels quels — leur
 * structure couvre 100% des besoins de cet API. Seule la semantique cote
 * service est plus restrictive (escalation + self-action + tenant fixe).
 * Le champ {@code hotelId} du {@link DBUserAdminDto} de retour reflete
 * naturellement le tenant courant.</p>
 *
 * <h2>Reset password</h2>
 * <p>Identique au SUPERADMIN : genere un mot de passe temporaire, hash BCrypt,
 * retourne le clair une fois. Idem : ne pas logger ce DTO.</p>
 */
public interface HotelUserService {

    /**
     * Liste paginee des users (actifs ET inactifs) de l'hotel courant.
     */
    Page<DBUserAdminDto> findAllInCurrentHotel(Pageable pageable);

    /**
     * Detail d'un user de l'hotel courant. {@link
     * com.cityprojects.citybackend.exception.ResourceNotFoundException}
     * (404) si {@code userId} n'existe pas OU n'appartient pas au tenant
     * courant (anti information leak).
     */
    DBUserAdminDto findById(Long userId);

    /**
     * Cree un user dans l'hotel courant.
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         {@code error.user.role.escalation.forbidden} si le role demande
     *         est SUPERADMIN ou ADMIN.
     */
    DBUserAdminDto create(DBUserCreateAdminDto dto);

    /**
     * Update PATCH d'un user de l'hotel courant. Refuse la promotion vers
     * SUPERADMIN/ADMIN. Refuse l'auto-edition (passer par /api/profile).
     */
    DBUserAdminDto update(Long userId, DBUserUpdateAdminDto dto);

    /** Verrouille un user (interdit l'auto-verrouillage). */
    void verrouiller(Long userId);

    /** Deverrouille un user. */
    void deverrouiller(Long userId);

    /**
     * Reset password : genere un mdp temp, hash, persiste, retourne le clair
     * une fois. Refuse l'auto-reset (l'ADMIN passe par /api/profile/me/change-password).
     */
    DBUserResetPasswordResponseDto resetPassword(Long userId);

    /** Soft delete (actif=false). Interdit l'auto-desactivation. */
    void desactiver(Long userId);
}
