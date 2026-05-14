package com.cityprojects.citybackend.service.profile;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.profile.ChangePasswordDto;
import com.cityprojects.citybackend.dto.profile.ProfileDto;
import com.cityprojects.citybackend.dto.profile.ProfileUpdateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link ProfileService}.
 *
 * <p>Couverture (Tour A) :
 * <ol>
 *   <li>T1 - findCurrent() retourne le profil du user authentifie + avatarUrl null
 *       quand pas d'avatar.</li>
 *   <li>T2 - updateCurrent() applique prenom/nom/telephone/poste sans toucher
 *       username, email, role, hotel.</li>
 *   <li>T3 - changePassword() OK : ancien match, nouveau valide, hash rote,
 *       motPasseTemporaire bascule a false.</li>
 *   <li>T4 - changePassword() refuse si ancienMotDePasse incorrect
 *       (BusinessException error.user.password.invalid).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfileServiceImplTests {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        tx = new TransactionTemplate(transactionManager);

        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel hotel = new Hotel("MR1", "Hotel Test");
        hotel.setCodePays("MR");
        Hotel savedHotel = hotelRepository.saveAndFlush(hotel);

        Role role = new Role("RECEPTION", "Reception");
        role.setActif(Boolean.TRUE);
        Role savedRole = roleRepository.saveAndFlush(role);

        DBUser user = new DBUser();
        user.setUsername("alice.test");
        user.setEmail("alice@test.mr");
        user.setPasswordHash(PasswordUtil.hashPassword("Init1234!Pwd"));
        user.setPrenom("Alice");
        user.setNom("Test");
        user.setTelephone("+22240000000");
        user.setPoste("Reception");
        user.setHotel(savedHotel);
        user.setRole(savedRole);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        user.setTentativesConnexion(0);
        user.setMotPasseTemporaire(Boolean.TRUE);
        userId = userRepository.saveAndFlush(user).getUserId();

        // SecurityContext peuple : ProfileService lit userId via SecurityUtils.
        // UserPrincipal.create() lit hotelCode/roleCode (LAZY) -> wrap dans TX.
        UserPrincipal principal = tx.execute(s -> {
            DBUser persisted = userRepository.findById(userId).orElseThrow();
            return UserPrincipal.create(persisted,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_RECEPTION")));
        });
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    @Test
    @DisplayName("T1 - findCurrent() retourne le profil du user authentifie, avatarUrl null par defaut")
    void shouldFindCurrentProfile() {
        ProfileDto dto = profileService.findCurrent();

        assertNotNull(dto);
        assertEquals(userId, dto.userId());
        assertEquals("alice.test", dto.username());
        assertEquals("alice@test.mr", dto.email());
        assertEquals("Alice", dto.prenom());
        assertEquals("Test", dto.nom());
        assertEquals("Alice Test", dto.nomComplet());
        assertEquals("RECEPTION", dto.roleCode());
        assertEquals("Hotel Test", dto.hotelNom());
        assertNull(dto.avatarUrl(), "Pas d'avatar => avatarUrl null");
        assertTrue(Boolean.TRUE.equals(dto.motPasseTemporaire()));
    }

    @Test
    @DisplayName("T2 - updateCurrent() applique prenom/nom/telephone/poste, username/email/role/hotel intacts")
    void shouldUpdateOwnProfileFieldsOnly() {
        ProfileUpdateDto patch = new ProfileUpdateDto(
                "Alicia", "Tester", "+22241112233", "Manager");

        ProfileDto updated = tx.execute(s -> profileService.updateCurrent(patch));

        assertEquals("Alicia", updated.prenom());
        assertEquals("Tester", updated.nom());
        assertEquals("+22241112233", updated.telephone());
        assertEquals("Manager", updated.poste());
        // Champs immuables conserves
        assertEquals("alice.test", updated.username());
        assertEquals("alice@test.mr", updated.email());
        assertEquals("RECEPTION", updated.roleCode());
        assertEquals("Hotel Test", updated.hotelNom());

        // En BDD : meme constat (pas d'autre champ touche)
        DBUser fromDb = userRepository.findById(userId).orElseThrow();
        assertEquals("Alicia", fromDb.getPrenom());
        assertEquals("alice.test", fromDb.getUsername(),
                "username doit rester immuable via /api/profile");
        assertEquals("alice@test.mr", fromDb.getEmail(),
                "email doit rester immuable via /api/profile");
    }

    @Test
    @DisplayName("T3 - changePassword() OK : hash rote, motPasseTemporaire bascule a false")
    void shouldChangePasswordHappyPath() {
        String oldHash = userRepository.findById(userId).orElseThrow().getPasswordHash();

        ChangePasswordDto dto = new ChangePasswordDto(
                "Init1234!Pwd", "NewPass987!XY", "NewPass987!XY");

        tx.executeWithoutResult(s -> profileService.changePassword(dto));

        DBUser fromDb = userRepository.findById(userId).orElseThrow();
        assertNotEquals(oldHash, fromDb.getPasswordHash(),
                "Le hash doit etre regenere");
        assertTrue(PasswordUtil.verifyPassword("NewPass987!XY", fromDb.getPasswordHash()),
                "Le nouveau clair doit verifier le nouveau hash");
        assertFalse(PasswordUtil.verifyPassword("Init1234!Pwd", fromDb.getPasswordHash()),
                "L'ancien clair ne doit plus verifier");
        assertFalse(Boolean.TRUE.equals(fromDb.getMotPasseTemporaire()),
                "motPasseTemporaire doit basculer a false");
    }

    @Test
    @DisplayName("T4 - changePassword() refuse si ancienMotDePasse incorrect (error.user.password.invalid)")
    void shouldRejectWrongOldPassword() {
        ChangePasswordDto dto = new ChangePasswordDto(
                "WrongOldPwd1!", "NewPass987!XY", "NewPass987!XY");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.executeWithoutResult(s -> profileService.changePassword(dto)));
        assertEquals("error.user.password.invalid", ex.getMessage());

        // Le hash en BDD doit etre intact (Init1234!Pwd verifie toujours)
        DBUser fromDb = userRepository.findById(userId).orElseThrow();
        assertTrue(PasswordUtil.verifyPassword("Init1234!Pwd", fromDb.getPasswordHash()),
                "Le hash doit etre intact en cas d'echec");
    }
}
