package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserResetPasswordResponseDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service technique de gestion des utilisateurs par un SUPERADMIN.
 *
 * <h2>Service cross-tenant</h2>
 * <p>Volontairement <b>hors</b> de l'invariant {@code @RequireTenant}. Le
 * SUPERADMIN agit cross-tenant et doit explicitement designer le
 * {@code hotelId} cible pour chaque operation. La securite est garantie
 * par {@code @PreAuthorize("hasRole('SUPERADMIN')")} cote controller.</p>
 *
 * <h2>Garde cross-hotel</h2>
 * <p>Toute operation par {@code (hotelId, userId)} verifie que
 * l'utilisateur appartient bien a l'hotel : si {@code user.hotel.hotelId
 * != hotelId} -&gt; {@code ResourceNotFoundException("error.user.notFound")}.
 * Cette garde empeche un appel POST/PUT mal cable (ex.
 * {@code /hotels/2/users/<userId-de-hotel-1>}) de muter un user d'un autre
 * tenant via une route apparente du tenant 2.</p>
 *
 * <h2>Reset password</h2>
 * <p>{@link #resetPassword(Long, Long)} regenere un mot de passe temporaire
 * (12 caracteres securises), le hash via BCrypt et le persiste, puis
 * <b>retourne le clair une seule fois</b>. L'admin doit le communiquer a
 * l'utilisateur par canal hors-bande. Pas de "send email" au tour 31.</p>
 */
public interface DBUserAdminService {

    /**
     * Page de tous les utilisateurs, tous hotels confondus. Reserve aux
     * vues globales SUPERADMIN ; volume susceptible d'etre eleve sur de
     * gros parcs : pagination obligatoire.
     */
    Page<DBUserAdminDto> findAllUsersAllHotels(Pageable pageable);

    /**
     * Page des utilisateurs (actifs ET inactifs) d'un hotel donne.
     */
    Page<DBUserAdminDto> findAllByHotel(Long hotelId, Pageable pageable);

    /**
     * Recupere un utilisateur d'un hotel donne. Garde cross-hotel : si
     * {@code user.hotel.hotelId != hotelId} -&gt; 404.
     */
    DBUserAdminDto findById(Long hotelId, Long userId);

    /**
     * Cree un nouvel utilisateur dans un hotel donne. Le {@code hotelId}
     * provient du path-param du controller (pas du DTO).
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         si {@code username} ou {@code email} existe deja, ou si le
     *         {@code roleId} n'est pas valide / actif.
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si l'hotel ou le role specifies n'existent pas.
     */
    DBUserAdminDto createForHotel(Long hotelId, DBUserCreateAdminDto dto);

    /**
     * Met a jour un utilisateur d'un hotel donne. Champs {@code null} non
     * appliques. Garde cross-hotel : 404 si l'utilisateur n'appartient pas
     * a l'hotel.
     */
    DBUserAdminDto update(Long hotelId, Long userId, DBUserUpdateAdminDto dto);

    /**
     * Verrouille un compte (compteVerrouille=true). Le user ne pourra plus
     * s'authentifier tant qu'un admin ne le {@link #deverrouiller(Long, Long)}
     * pas. Idempotent.
     */
    void verrouiller(Long hotelId, Long userId);

    /**
     * Deverrouille un compte (compteVerrouille=false, tentatives=0). Idempotent.
     */
    void deverrouiller(Long hotelId, Long userId);

    /**
     * Reset le mot de passe : genere un mot de passe temporaire en clair,
     * le hash et le persiste, retourne le clair une seule fois.
     */
    DBUserResetPasswordResponseDto resetPassword(Long hotelId, Long userId);

    /**
     * Desactive un utilisateur (actif=false) — soft delete. L'historique
     * (sessions, audits, ...) est conserve. Idempotent.
     */
    void desactiver(Long hotelId, Long userId);
}
