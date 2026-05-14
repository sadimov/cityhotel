package com.cityprojects.citybackend.service.profile;

import com.cityprojects.citybackend.common.security.SecurityUtils;
import com.cityprojects.citybackend.dto.profile.ChangePasswordDto;
import com.cityprojects.citybackend.dto.profile.ProfileDto;
import com.cityprojects.citybackend.dto.profile.ProfileUpdateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.profile.ProfileMapper;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation du {@link ProfileService}.
 *
 * <h2>Securite</h2>
 * <ul>
 *   <li>{@code userId} resolu via {@link SecurityUtils#currentUserIdOrThrow()} —
 *       jamais lu depuis un path/query/body. Garantit qu'un utilisateur ne peut
 *       editer QUE son propre profil via cet API.</li>
 *   <li>Pas de {@code @RequireTenant} : opere sur le user courant, le tenant
 *       est deja garanti coherent par le JWT (le user appartient a son tenant).</li>
 *   <li>{@code passwordHash} jamais retourne (le DTO ne l'a pas, le mapper ne
 *       l'aplatit pas, les logs ne le mentionnent jamais).</li>
 * </ul>
 *
 * <h2>Avatar URL publique</h2>
 * <p>Format <code>/uploads/avatars/{filename}</code> (servi en static via
 * {@link com.cityprojects.citybackend.config.WebConfig}). Tour A : URL publique
 * non auth-protected — le filename contient un UUID donc peu devinable. Si on
 * doit basculer en endpoint auth-protected (e.g. avatars sensibles), prevoir
 * un controller {@code GET /api/profile/avatars/{filename}} qui re-lit depuis
 * le meme service et requiert {@code authenticated()}.</p>
 */
@Service
@Transactional(readOnly = true)
public class ProfileServiceImpl implements ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class);

    /** Prefixe public des avatars (assemble dans le DTO). */
    static final String AVATAR_URL_PREFIX = "/uploads/avatars/";

    private final DBUserRepository userRepository;
    private final ProfileMapper profileMapper;
    private final AvatarStorageService avatarStorage;

    public ProfileServiceImpl(DBUserRepository userRepository,
                              ProfileMapper profileMapper,
                              AvatarStorageService avatarStorage) {
        this.userRepository = userRepository;
        this.profileMapper = profileMapper;
        this.avatarStorage = avatarStorage;
    }

    @Override
    public ProfileDto findCurrent() {
        return toDto(currentUser());
    }

    @Override
    @Transactional
    public ProfileDto updateCurrent(ProfileUpdateDto dto) {
        DBUser user = currentUser();
        user.setPrenom(dto.prenom().trim());
        user.setNom(dto.nom().trim());
        user.setTelephone(emptyToNull(dto.telephone()));
        user.setPoste(emptyToNull(dto.poste()));
        DBUser saved = userRepository.save(user);
        logger.info("Profile: update infos perso userId={}", saved.getUserId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDto dto) {
        DBUser user = currentUser();

        if (!PasswordUtil.verifyPassword(dto.ancienMotDePasse(), user.getPasswordHash())) {
            // Pas de log du clair, ni du hash : on signale juste l'echec.
            logger.info("Profile: change-password refuse (ancien KO) userId={}", user.getUserId());
            throw new BusinessException("error.user.password.invalid");
        }

        if (!dto.nouveauMotDePasse().equals(dto.confirmation())) {
            throw new BusinessException("error.user.password.mismatch");
        }

        if (dto.nouveauMotDePasse().equals(dto.ancienMotDePasse())) {
            throw new BusinessException("error.user.password.unchanged");
        }

        PasswordUtil.PasswordValidationResult check = PasswordUtil.validatePassword(dto.nouveauMotDePasse());
        if (!check.isValid()) {
            // Pas de details d'erreur (ne pas leak la politique exacte ; cle i18n cote front).
            throw new BusinessException("error.user.password.weak");
        }

        user.setPasswordHash(PasswordUtil.hashPassword(dto.nouveauMotDePasse()));
        user.setMotPasseTemporaire(Boolean.FALSE);
        userRepository.save(user);
        logger.info("Profile: password rote userId={}", user.getUserId());
    }

    @Override
    @Transactional
    public ProfileDto uploadAvatar(MultipartFile file) {
        DBUser user = currentUser();
        String previous = readAvatarFilename(user);
        String filename = avatarStorage.store(user.getUserId(), file, previous);
        writeAvatarFilename(user, filename);
        DBUser saved = userRepository.save(user);
        logger.info("Profile: avatar uploade userId={} filename={}", saved.getUserId(), filename);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProfileDto deleteAvatar() {
        DBUser user = currentUser();
        String previous = readAvatarFilename(user);
        if (previous != null && !previous.isBlank()) {
            avatarStorage.delete(previous);
            writeAvatarFilename(user, null);
            userRepository.save(user);
            logger.info("Profile: avatar supprime userId={} filename={}", user.getUserId(), previous);
        }
        return toDto(user);
    }

    /**
     * Charge l'utilisateur courant depuis la BDD. {@link
     * SecurityUtils#currentUserIdOrThrow()} leve {@code error.user.unknown}
     * si pas d'identite resolue (e.g. job programme sans auth).
     */
    private DBUser currentUser() {
        Long userId = SecurityUtils.currentUserIdOrThrow();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
    }

    /**
     * Assemble le {@link ProfileDto} avec l'{@code avatarUrl} publique.
     * Centralise la conversion entite -&gt; DTO pour les 5 endpoints.
     */
    private ProfileDto toDto(DBUser user) {
        ProfileDto base = profileMapper.toDto(user);
        String filename = readAvatarFilename(user);
        String url = (filename == null || filename.isBlank()) ? null : AVATAR_URL_PREFIX + filename;
        return profileMapper.withAvatarUrl(base, url);
    }

    /**
     * Acces au champ {@code avatar_filename} de l'entite {@link DBUser}.
     * Encapsulee ici pour faciliter une evolution future si le storage
     * devient externe (S3, CDN signe, etc.).
     */
    private String readAvatarFilename(DBUser user) {
        return user.getAvatarFilename();
    }

    private void writeAvatarFilename(DBUser user, String filename) {
        user.setAvatarFilename(filename);
    }

    private static String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
