package com.cityprojects.citybackend.service.profile;

import com.cityprojects.citybackend.dto.profile.ChangePasswordDto;
import com.cityprojects.citybackend.dto.profile.ProfileDto;
import com.cityprojects.citybackend.dto.profile.ProfileUpdateDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service de gestion du profil self-service.
 *
 * <h2>Pas de @RequireTenant</h2>
 * <p>Toutes les operations sont scopees a l'<b>utilisateur courant</b> resolu
 * via {@link com.cityprojects.citybackend.common.security.SecurityUtils#currentUserIdOrThrow()},
 * et non a un tenant explicite. Le {@code hotelId} n'apparait nulle part en
 * parametre — c'est intentionnel. Un user authentifie n'a pas besoin du
 * tenant pour modifier ses infos perso.</p>
 *
 * <h2>Champs immutables via cet API</h2>
 * <p>{@code username}, {@code email}, {@code role}, {@code hotel}, {@code actif}
 * ne peuvent pas etre modifies via le self-service. Workflow change-email =
 * tour separe (validation par token mail).</p>
 */
public interface ProfileService {

    /**
     * Renvoie le profil complet de l'utilisateur courant. Leve
     * {@link com.cityprojects.citybackend.exception.BusinessException}
     * ({@code error.user.unknown}) si pas d'identite resolue.
     */
    ProfileDto findCurrent();

    /**
     * Applique le DTO PATCH (prenom + nom + telephone + poste). {@code username},
     * {@code email}, {@code role}, {@code hotel}, {@code actif} restent immuables.
     */
    ProfileDto updateCurrent(ProfileUpdateDto dto);

    /**
     * Rotation du mot de passe self-service. Verifie ancien BCrypt, valide
     * nouveau (regex + dictionnaire commun via {@code PasswordUtil}), persiste
     * le nouveau hash, force {@code motPasseTemporaire=false}.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException si
     *         ancien faux ({@code error.user.password.invalid}), nouveau != confirmation
     *         ({@code error.user.password.mismatch}), nouveau == ancien
     *         ({@code error.user.password.unchanged}), ou faible
     *         ({@code error.user.password.weak}).
     */
    void changePassword(ChangePasswordDto dto);

    /**
     * Upload d'avatar. Validation type + taille via {@link AvatarStorageService}.
     * Renvoie le profil mis a jour (avec {@code avatarUrl} positionne).
     */
    ProfileDto uploadAvatar(MultipartFile file);

    /**
     * Supprime l'avatar courant (fichier disque + clear de la colonne BDD).
     * Idempotent : ne leve pas si pas d'avatar.
     */
    ProfileDto deleteAvatar();
}
