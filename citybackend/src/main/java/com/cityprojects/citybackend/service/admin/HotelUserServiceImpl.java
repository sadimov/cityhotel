package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.security.SecurityUtils;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserResetPasswordResponseDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.admin.DBUserAdminMapper;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementation du {@link HotelUserService}.
 *
 * <h2>Decision de positionnement</h2>
 * <p>Package {@code service.admin} (au lieu d'un nouveau {@code service.hoteladmin})
 * pour rester proche du {@link DBUserAdminService} qu'il complete — le code
 * partage les memes DTOs et la meme logique generale. La distinction
 * SUPERADMIN vs ADMIN d'hotel se fait au niveau du <b>controller</b> (route
 * differente + @PreAuthorize different).</p>
 *
 * <h2>Pas de @RequireTenant cote classe</h2>
 * <p>L'aspect {@code @RequireTenant} verifie {@code TenantContext.getOrNull() != null}
 * — exactement ce qu'on veut ici. Les operations sont par definition scopees
 * au tenant courant : {@link #currentHotelId()} resoud le tenant et leve
 * {@code IllegalStateException("error.tenant.missing")} si absent (cas qui
 * ne devrait jamais arriver en production via {@code /api/hotel/**} car le
 * JwtAuthenticationFilter pose le TenantContext, mais defense en profondeur).</p>
 *
 * <p><b>Note</b> : on N'utilise PAS {@link com.cityprojects.citybackend.common.tenant.TenantScope}
 * ici (a la difference de {@link DBUserAdminServiceImpl}) — le tenant est
 * deja celui du JWT, pas un tenant explicite "agir-pour-le-compte-de".</p>
 *
 * <h2>Roles interdits a la creation/promotion</h2>
 * <p>{@link #FORBIDDEN_ROLES} = {@code SUPERADMIN}, {@code ADMIN}. Un ADMIN
 * d'hotel ne peut pas creer un autre ADMIN (laissons cette prerogative au
 * SUPERADMIN, qui en fait au demarrage initial du tenant). Les roles ouverts
 * sont GERANT, RECEPTION, RESTAURANT, RESREC, MAGASIN, MENAGE, NIGHTAUDIT
 * (et autres futurs roles operationnels).</p>
 */
@Service
@Transactional(readOnly = true)
public class HotelUserServiceImpl implements HotelUserService {

    private static final Logger logger = LoggerFactory.getLogger(HotelUserServiceImpl.class);

    private static final int TEMP_PASSWORD_LENGTH = 12;

    /**
     * Roles que l'ADMIN d'hotel ne peut ni attribuer ni promouvoir. SUPERADMIN
     * est cross-tenant, ADMIN doit etre amorce par le SUPERADMIN (chain of trust).
     */
    static final Set<String> FORBIDDEN_ROLES = Set.of("SUPERADMIN", "ADMIN");

    private final DBUserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoleRepository roleRepository;
    private final DBUserAdminMapper userMapper;

    public HotelUserServiceImpl(DBUserRepository userRepository,
                                HotelRepository hotelRepository,
                                RoleRepository roleRepository,
                                DBUserAdminMapper userMapper) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    @Override
    public Page<DBUserAdminDto> findAllInCurrentHotel(Pageable pageable) {
        Long hotelId = currentHotelId();
        // findByHotelHotelIdAndActifTrue filtre par jointure SQL.
        // Pas besoin de TenantScope : DBUser n'est pas encore @TenantId
        // (cf. DBUserRepository javadoc - migration cible).
        return userRepository.findByHotelHotelIdAndActifTrue(hotelId, pageable)
                .map(userMapper::toDto);
    }

    @Override
    public DBUserAdminDto findById(Long userId) {
        Long hotelId = currentHotelId();
        DBUser user = loadUserInHotel(userId, hotelId);
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public DBUserAdminDto create(DBUserCreateAdminDto dto) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: creation user username={} hotelId={}", dto.username(), hotelId);

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound"));

        if (userRepository.existsByUsername(dto.username())) {
            throw new BusinessException("error.user.username.alreadyExists");
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new BusinessException("error.user.email.alreadyExists");
        }

        Role role = roleRepository.findById(dto.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notFound"));
        if (!Boolean.TRUE.equals(role.getActif())) {
            throw new BusinessException("error.role.inactive");
        }
        // Anti-escalation : l'ADMIN ne peut creer ni SUPERADMIN ni ADMIN.
        ensureRoleAllowed(role);

        DBUser user = new DBUser();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setPasswordHash(PasswordUtil.hashPassword(dto.password()));
        user.setPrenom(dto.prenom());
        user.setNom(dto.nom());
        user.setTelephone(dto.telephone());
        user.setPoste(dto.poste());
        user.setHotel(hotel);
        user.setRole(role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        user.setTentativesConnexion(0);
        DBUser saved = userRepository.save(user);
        logger.info("HotelAdmin: user cree id={} username={}", saved.getUserId(), saved.getUsername());
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public DBUserAdminDto update(Long userId, DBUserUpdateAdminDto dto) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: update user id={} hotelId={}", userId, hotelId);
        ensureNotSelf(userId);

        DBUser user = loadUserInHotel(userId, hotelId);

        if (dto.email() != null && !dto.email().isBlank()
                && !dto.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailAndUserIdNot(dto.email(), userId)) {
            throw new BusinessException("error.user.email.alreadyExists");
        }

        if (dto.email() != null) {
            user.setEmail(dto.email());
        }
        if (dto.prenom() != null) {
            user.setPrenom(dto.prenom());
        }
        if (dto.nom() != null) {
            user.setNom(dto.nom());
        }
        if (dto.telephone() != null) {
            user.setTelephone(dto.telephone());
        }
        if (dto.poste() != null) {
            user.setPoste(dto.poste());
        }
        if (dto.roleId() != null) {
            Role role = roleRepository.findById(dto.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.role.notFound"));
            if (!Boolean.TRUE.equals(role.getActif())) {
                throw new BusinessException("error.role.inactive");
            }
            // Anti-escalation : refuser la promotion vers SUPERADMIN/ADMIN.
            ensureRoleAllowed(role);
            user.setRole(role);
        }

        return userMapper.toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public void verrouiller(Long userId) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: verrouillage user id={} hotelId={}", userId, hotelId);
        ensureNotSelf(userId);
        DBUser user = loadUserInHotel(userId, hotelId);
        if (Boolean.TRUE.equals(user.getCompteVerrouille())) {
            return;
        }
        user.setCompteVerrouille(Boolean.TRUE);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deverrouiller(Long userId) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: deverrouillage user id={} hotelId={}", userId, hotelId);
        DBUser user = loadUserInHotel(userId, hotelId);
        // Deverrouillage tolere sur soi-meme (un ADMIN peut se debloquer si
        // verrouille par tentatives multiples, sans cumuler 2 erreurs : suicide
        // anti-escalation = MODIFICATION (qui pourrait basculer son role).
        // Hors scope : ce serait via un endpoint reset specifique cote SUPERADMIN.
        // Ici on autorise pour rester pragmatique.
        if (!Boolean.TRUE.equals(user.getCompteVerrouille())
                && (user.getTentativesConnexion() == null || user.getTentativesConnexion() == 0)) {
            return;
        }
        user.setCompteVerrouille(Boolean.FALSE);
        user.setTentativesConnexion(0);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public DBUserResetPasswordResponseDto resetPassword(Long userId) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: reset password user id={} hotelId={}", userId, hotelId);
        ensureNotSelf(userId);
        DBUser user = loadUserInHotel(userId, hotelId);

        String tempPassword = PasswordUtil.generateSecurePassword(TEMP_PASSWORD_LENGTH);
        user.setPasswordHash(PasswordUtil.hashPassword(tempPassword));
        user.setTentativesConnexion(0);
        user.setCompteVerrouille(Boolean.FALSE);
        user.setMotPasseTemporaire(Boolean.TRUE);
        userRepository.save(user);

        // NE PAS logger tempPassword (clair). Retourne au caller, jamais reloggable.
        return new DBUserResetPasswordResponseDto(user.getUserId(), user.getUsername(), tempPassword);
    }

    @Override
    @Transactional
    public void desactiver(Long userId) {
        Long hotelId = currentHotelId();
        logger.info("HotelAdmin: desactivation user id={} hotelId={}", userId, hotelId);
        ensureNotSelf(userId);
        DBUser user = loadUserInHotel(userId, hotelId);
        if (Boolean.FALSE.equals(user.getActif())) {
            return;
        }
        user.setActif(Boolean.FALSE);
        userRepository.save(user);
    }

    /**
     * Resout le tenant courant. Leve {@code IllegalStateException} si absent
     * (le {@code GlobalExceptionHandler} mappe en 409 — signal d'un bug
     * d'authentification cote infra).
     */
    private Long currentHotelId() {
        return TenantContext.get();
    }

    /**
     * Charge un user en verifiant son appartenance au tenant. Refuse 404
     * (anti information leak) si le user n'existe pas OU appartient a un
     * autre hotel.
     */
    private DBUser loadUserInHotel(Long userId, Long hotelId) {
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        if (user.getHotel() == null || !hotelId.equals(user.getHotel().getHotelId())) {
            throw new ResourceNotFoundException("error.user.notFound");
        }
        return user;
    }

    /**
     * Verifie que le {@code targetUserId} n'est pas l'ADMIN courant lui-meme.
     * Anti-suicide : empeche un ADMIN de se desactiver / verrouiller /
     * modifier role, ce qui le laisserait sans moyen de revenir.
     */
    private void ensureNotSelf(Long targetUserId) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            throw new BusinessException("error.user.self.action.forbidden");
        }
    }

    /**
     * Anti-escalation : refuse {@code SUPERADMIN} et {@code ADMIN}. L'ADMIN
     * d'hotel ne peut creer / promouvoir QUE des roles operationnels.
     */
    private void ensureRoleAllowed(Role role) {
        if (role == null || role.getRoleCode() == null
                || FORBIDDEN_ROLES.contains(role.getRoleCode())) {
            throw new BusinessException("error.user.role.escalation.forbidden");
        }
    }
}
