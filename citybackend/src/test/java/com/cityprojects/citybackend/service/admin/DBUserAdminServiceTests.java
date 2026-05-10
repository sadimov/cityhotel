package com.cityprojects.citybackend.service.admin;

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
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link DBUserAdminService}.
 *
 * <p>Couverture :
 * <ol>
 *   <li>T1 : createForHotel() persiste avec hash BCrypt (pas le clair).</li>
 *   <li>T2 : update() applique en PATCH (semantique null = ne pas toucher).</li>
 *   <li>T3 : resetPassword() retourne un mdp temp en clair, le hash est persiste,
 *       le clair n'est plus le hash anterieur.</li>
 *   <li>T4 : verrouiller / deverrouiller change compteVerrouille et reset
 *       tentativesConnexion ; idempotent.</li>
 *   <li>T5 : update cross-hotel (path /hotels/2/users/{userId-de-hotel-1})
 *       -&gt; ResourceNotFoundException (404 cote HTTP, anti-leak).</li>
 *   <li>T6 : createForHotel() refuse un username deja existant.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class DBUserAdminServiceTests {

    @Autowired
    private DBUserAdminService userAdminService;

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

    private TransactionTemplate transactionTemplate;
    private Long hotelMrId;
    private Long hotelFrId;
    private Integer roleGerantId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);

        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role gerant = new Role("GERANT", "Gerant");
        gerant.setActif(Boolean.TRUE);
        roleGerantId = roleRepository.saveAndFlush(gerant).getRoleId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    @Test
    @DisplayName("T1 - createForHotel() persiste avec hash BCrypt (le clair n'est jamais en base)")
    void shouldCreateUserWithBcryptHash() {
        DBUserCreateAdminDto dto = new DBUserCreateAdminDto(
                "karim.s", "karim@mr.test", "Secret123!Demo",
                "Karim", "Sow", "+22245111000", "Reception", roleGerantId);

        DBUserAdminDto created = transactionTemplate.execute(s ->
                userAdminService.createForHotel(hotelMrId, dto));

        assertNotNull(created);
        assertNotNull(created.userId());
        assertEquals("karim.s", created.username());
        assertEquals(hotelMrId, created.hotelId());
        assertTrue(Boolean.TRUE.equals(created.actif()));

        // Verifier en base que le hash est BCrypt et pas le clair.
        DBUser fromDb = userRepository.findById(created.userId()).orElseThrow();
        assertNotEquals("Secret123!Demo", fromDb.getPasswordHash(),
                "Le clair ne doit JAMAIS etre persiste");
        assertTrue(PasswordUtil.verifyPassword("Secret123!Demo", fromDb.getPasswordHash()),
                "Le hash doit verifier le clair via BCrypt");
    }

    @Test
    @DisplayName("T2 - update() applique en semantique PATCH (null = ne pas toucher)")
    void shouldUpdateUserPartial() {
        DBUserAdminDto created = transactionTemplate.execute(s ->
                userAdminService.createForHotel(hotelMrId, new DBUserCreateAdminDto(
                        "u.patch", "patch@mr.test", "Secret123!Init",
                        "Init", "User", "+22245000000", "Init Poste", roleGerantId)));

        DBUserUpdateAdminDto patch = new DBUserUpdateAdminDto(
                null, "Updated", null, "+22245999999", null, null);

        DBUserAdminDto updated = transactionTemplate.execute(s ->
                userAdminService.update(hotelMrId, created.userId(), patch));

        // prenom remplace
        assertEquals("Updated", updated.prenom());
        // nom intact (patch null)
        assertEquals("User", updated.nom());
        assertEquals("+22245999999", updated.telephone());
        // poste intact (patch null)
        assertEquals("Init Poste", updated.poste());
        // email intact
        assertEquals("patch@mr.test", updated.email());
    }

    @Test
    @DisplayName("T3 - resetPassword() : nouveau hash, mdp temp en clair retourne, ancien hash invalide")
    void shouldResetPasswordAndReturnTempInClear() {
        DBUserAdminDto created = transactionTemplate.execute(s ->
                userAdminService.createForHotel(hotelMrId, new DBUserCreateAdminDto(
                        "u.reset", "reset@mr.test", "OriginalPwd1!",
                        "R", "U", null, null, roleGerantId)));

        String oldHash = userRepository.findById(created.userId()).orElseThrow().getPasswordHash();

        DBUserResetPasswordResponseDto resp = transactionTemplate.execute(s ->
                userAdminService.resetPassword(hotelMrId, created.userId()));

        assertNotNull(resp);
        assertEquals(created.userId(), resp.userId());
        assertEquals("u.reset", resp.username());
        assertNotNull(resp.temporaryPassword());
        assertTrue(resp.temporaryPassword().length() >= 8);

        DBUser fromDb = userRepository.findById(created.userId()).orElseThrow();
        // Hash a change
        assertNotEquals(oldHash, fromDb.getPasswordHash(), "Le hash doit etre regenere");
        // Le clair retourne verifie le nouveau hash
        assertTrue(PasswordUtil.verifyPassword(resp.temporaryPassword(), fromDb.getPasswordHash()));
        // L'ancien clair ne verifie plus le nouveau hash
        assertFalse(PasswordUtil.verifyPassword("OriginalPwd1!", fromDb.getPasswordHash()));
    }

    @Test
    @DisplayName("T4 - verrouiller/deverrouiller : compteVerrouille toggle, tentatives reset, idempotent")
    void shouldVerrouillerAndDeverrouiller() {
        DBUserAdminDto created = transactionTemplate.execute(s ->
                userAdminService.createForHotel(hotelMrId, new DBUserCreateAdminDto(
                        "u.lock", "lock@mr.test", "AnyPwd123!",
                        "L", "K", null, null, roleGerantId)));

        // Simuler tentatives en base
        DBUser u = userRepository.findById(created.userId()).orElseThrow();
        u.setTentativesConnexion(5);
        userRepository.saveAndFlush(u);

        transactionTemplate.executeWithoutResult(s ->
                userAdminService.verrouiller(hotelMrId, created.userId()));
        DBUser locked = userRepository.findById(created.userId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(locked.getCompteVerrouille()));

        // Idempotent
        transactionTemplate.executeWithoutResult(s ->
                userAdminService.verrouiller(hotelMrId, created.userId()));

        transactionTemplate.executeWithoutResult(s ->
                userAdminService.deverrouiller(hotelMrId, created.userId()));
        DBUser unlocked = userRepository.findById(created.userId()).orElseThrow();
        assertFalse(Boolean.TRUE.equals(unlocked.getCompteVerrouille()));
        assertEquals(0, unlocked.getTentativesConnexion(),
                "Deverrouiller doit aussi reset tentativesConnexion");
    }

    @Test
    @DisplayName("T5 - update cross-hotel (/hotels/{autre}/users/{userId-de-hotel-1}) -> 404 ResourceNotFoundException")
    void shouldRejectCrossHotelUpdate() {
        DBUserAdminDto createdInMr = transactionTemplate.execute(s ->
                userAdminService.createForHotel(hotelMrId, new DBUserCreateAdminDto(
                        "u.crossh", "crossh@mr.test", "AnyPwd123!",
                        "X", "H", null, null, roleGerantId)));

        DBUserUpdateAdminDto patch = new DBUserUpdateAdminDto(
                "evil@hack.test", null, null, null, null, null);

        // Tente d'updater le user de hotel MR via la route hotel FR
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(s -> userAdminService.update(
                        hotelFrId, createdInMr.userId(), patch)));
        assertEquals("error.user.notFound", ex.getMessage());

        // Verifier que le user n'a PAS ete modifie
        DBUser unchanged = userRepository.findById(createdInMr.userId()).orElseThrow();
        assertEquals("crossh@mr.test", unchanged.getEmail(),
                "L'email ne doit pas avoir ete modifie cross-hotel");
    }

    @Test
    @DisplayName("T6 - createForHotel() refuse username deja existant -> BusinessException")
    void shouldRejectDuplicateUsername() {
        transactionTemplate.execute(s -> userAdminService.createForHotel(
                hotelMrId, new DBUserCreateAdminDto(
                        "u.dup", "dup@mr.test", "Pwd123456!",
                        "D", "U", null, null, roleGerantId)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionTemplate.execute(s -> userAdminService.createForHotel(
                        hotelMrId, new DBUserCreateAdminDto(
                                "u.dup", "other@mr.test", "Pwd123456!",
                                "D2", "U2", null, null, roleGerantId))));
        assertEquals("error.user.username.alreadyExists", ex.getMessage());
    }
}
