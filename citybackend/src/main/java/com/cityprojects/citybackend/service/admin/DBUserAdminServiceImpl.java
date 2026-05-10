package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.tenant.TenantScope;
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

/**
 * Implementation de {@link DBUserAdminService}.
 *
 * <h2>Pas de @RequireTenant</h2>
 * <p>Service technique cross-tenant. Securite via
 * {@code @PreAuthorize("hasRole('SUPERADMIN')")} cote controller. Les
 * operations sur des entites tenant (DBUser appartient bien a un hotel) sont
 * neanmoins encadrees par {@link TenantScope#runAs(Long, java.util.function.Supplier)}
 * pour positionner le {@code TenantContext} le temps de l'INSERT/UPDATE :
 * cela rend explicite l'intention "j'agis pour le compte de cet hotel" et
 * prepare la migration future de DBUser vers {@code @TenantId}.</p>
 *
 * <h2>Garde cross-hotel</h2>
 * <p>{@link #ensureUserInHotel(Long, Long)} verifie que l'utilisateur charge
 * appartient bien au hotel cible — sinon {@code ResourceNotFoundException}
 * (404 cote HTTP). Cette regle empeche un appel route avec un userId
 * d'un autre hotel d'aboutir.</p>
 */
@Service
@Transactional(readOnly = true)
public class DBUserAdminServiceImpl implements DBUserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(DBUserAdminServiceImpl.class);

    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final DBUserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoleRepository roleRepository;
    private final DBUserAdminMapper userMapper;

    public DBUserAdminServiceImpl(DBUserRepository userRepository,
                                  HotelRepository hotelRepository,
                                  RoleRepository roleRepository,
                                  DBUserAdminMapper userMapper) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    @Override
    public Page<DBUserAdminDto> findAllUsersAllHotels(Pageable pageable) {
        // Vue globale cross-tenant : explicitement en mode ROOT.
        return TenantScope.runAsRoot(() ->
                userRepository.findAll(pageable).map(userMapper::toDto));
    }

    @Override
    public Page<DBUserAdminDto> findAllByHotel(Long hotelId, Pageable pageable) {
        ensureHotelExists(hotelId);
        // findByHotelHotelIdAndActifTrue filtre par jointure : ok meme sans
        // TenantContext. On encadre quand meme dans un runAs pour signaler
        // l'intention et preparer la migration future de DBUser vers @TenantId.
        return TenantScope.runAs(hotelId, () ->
                userRepository.findByHotelHotelIdAndActifTrue(hotelId, pageable)
                        .map(userMapper::toDto));
    }

    @Override
    public DBUserAdminDto findById(Long hotelId, Long userId) {
        ensureHotelExists(hotelId);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public DBUserAdminDto createForHotel(Long hotelId, DBUserCreateAdminDto dto) {
        logger.info("Admin: creation user username={} hotelId={}", dto.username(), hotelId);

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

        return TenantScope.runAs(hotelId, () -> {
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
            logger.info("Admin: user cree id={} username={}", saved.getUserId(), saved.getUsername());
            return userMapper.toDto(saved);
        });
    }

    @Override
    @Transactional
    public DBUserAdminDto update(Long hotelId, Long userId, DBUserUpdateAdminDto dto) {
        logger.info("Admin: update user id={} hotelId={}", userId, hotelId);
        ensureHotelExists(hotelId);

        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);

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
            user.setRole(role);
        }

        return TenantScope.runAs(hotelId, () -> userMapper.toDto(userRepository.save(user)));
    }

    @Override
    @Transactional
    public void verrouiller(Long hotelId, Long userId) {
        logger.info("Admin: verrouillage user id={} hotelId={}", userId, hotelId);
        ensureHotelExists(hotelId);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);
        if (Boolean.TRUE.equals(user.getCompteVerrouille())) {
            return;
        }
        user.setCompteVerrouille(Boolean.TRUE);
        TenantScope.runAs(hotelId, () -> userRepository.save(user));
    }

    @Override
    @Transactional
    public void deverrouiller(Long hotelId, Long userId) {
        logger.info("Admin: deverrouillage user id={} hotelId={}", userId, hotelId);
        ensureHotelExists(hotelId);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);
        if (!Boolean.TRUE.equals(user.getCompteVerrouille())
                && (user.getTentativesConnexion() == null || user.getTentativesConnexion() == 0)) {
            return;
        }
        user.setCompteVerrouille(Boolean.FALSE);
        user.setTentativesConnexion(0);
        TenantScope.runAs(hotelId, () -> userRepository.save(user));
    }

    @Override
    @Transactional
    public DBUserResetPasswordResponseDto resetPassword(Long hotelId, Long userId) {
        logger.info("Admin: reset password user id={} hotelId={}", userId, hotelId);
        ensureHotelExists(hotelId);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);

        String tempPassword = PasswordUtil.generateSecurePassword(TEMP_PASSWORD_LENGTH);
        user.setPasswordHash(PasswordUtil.hashPassword(tempPassword));
        // Reset tentatives + verrou : un mdp temp doit pouvoir etre utilise tout de suite.
        user.setTentativesConnexion(0);
        user.setCompteVerrouille(Boolean.FALSE);
        TenantScope.runAs(hotelId, () -> userRepository.save(user));

        // Le clair n'est jamais reloggable (ne pas l'inserer dans le logger.info ci-dessus).
        return new DBUserResetPasswordResponseDto(user.getUserId(), user.getUsername(), tempPassword);
    }

    @Override
    @Transactional
    public void desactiver(Long hotelId, Long userId) {
        logger.info("Admin: desactivation user id={} hotelId={}", userId, hotelId);
        ensureHotelExists(hotelId);
        DBUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notFound"));
        ensureUserInHotel(user, hotelId);
        if (Boolean.FALSE.equals(user.getActif())) {
            return;
        }
        user.setActif(Boolean.FALSE);
        TenantScope.runAs(hotelId, () -> userRepository.save(user));
    }

    private void ensureHotelExists(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("error.hotel.notFound");
        }
    }

    /**
     * Garde cross-hotel : refuse 404 si {@code user.hotel.hotelId != hotelId}.
     * Important : on ne renvoie <b>jamais</b> 403 ici, car cela divulguerait
     * l'existence d'un user dans un autre hotel (information leak). Le 404
     * est volontairement indiscernable d'un user inexistant.
     */
    private void ensureUserInHotel(DBUser user, Long hotelId) {
        if (user.getHotel() == null || !hotelId.equals(user.getHotel().getHotelId())) {
            throw new ResourceNotFoundException("error.user.notFound");
        }
    }
}
