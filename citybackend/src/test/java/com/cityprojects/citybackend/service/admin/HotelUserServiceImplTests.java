package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.admin.DBUserAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.DBUserUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Surefire (H2 + Spring) du {@link HotelUserService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - create() OK : user persiste dans le tenant courant + hash BCrypt
 *       + role autorise (GERANT).</li>
 *   <li>T2 - create() refuse role SUPERADMIN/ADMIN
 *       (BusinessException error.user.role.escalation.forbidden).</li>
 *   <li>T3 - update() refuse de modifier l'ADMIN courant lui-meme
 *       (BusinessException error.user.self.action.forbidden).</li>
 *   <li>T4 - findById() cross-hotel -&gt; ResourceNotFoundException
 *       (404, anti information leak).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class HotelUserServiceImplTests {

    @Autowired
    private HotelUserService hotelUserService;

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
    private Long hotelMrId;
    private Long hotelFrId;
    private Integer roleAdminId;
    private Integer roleGerantId;
    private Long adminCurrentUserId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        tx = new TransactionTemplate(transactionManager);

        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel mr = new Hotel("MR1", "Hotel Mauritanie");
        mr.setCodePays("MR");
        hotelMrId = hotelRepository.saveAndFlush(mr).getHotelId();

        Hotel fr = new Hotel("FR1", "Hotel France");
        fr.setCodePays("FR");
        hotelFrId = hotelRepository.saveAndFlush(fr).getHotelId();

        Role admin = new Role("ADMIN", "Admin Hotel");
        admin.setActif(Boolean.TRUE);
        roleAdminId = roleRepository.saveAndFlush(admin).getRoleId();

        Role gerant = new Role("GERANT", "Gerant");
        gerant.setActif(Boolean.TRUE);
        roleGerantId = roleRepository.saveAndFlush(gerant).getRoleId();

        // Cree l'ADMIN courant dans hotel MR
        Hotel mrPersisted = hotelRepository.findById(hotelMrId).orElseThrow();
        Role adminPersisted = roleRepository.findById(roleAdminId).orElseThrow();

        DBUser adminUser = new DBUser();
        adminUser.setUsername("admin.mr");
        adminUser.setEmail("admin@mr.test");
        adminUser.setPasswordHash(PasswordUtil.hashPassword("AdminPwd1!"));
        adminUser.setPrenom("Admin");
        adminUser.setNom("MR");
        adminUser.setHotel(mrPersisted);
        adminUser.setRole(adminPersisted);
        adminUser.setActif(Boolean.TRUE);
        adminUser.setCompteVerrouille(Boolean.FALSE);
        adminUser.setTentativesConnexion(0);
        adminCurrentUserId = userRepository.saveAndFlush(adminUser).getUserId();

        // SecurityContext + TenantContext = ADMIN du hotel MR.
        // Le UserPrincipal.create() touche au hotelCode/roleCode/etc qui sont LAZY :
        // on doit etre dans une session Hibernate active -> wrap dans TX.
        UserPrincipal principal = tx.execute(s -> {
            DBUser persisted = userRepository.findById(adminCurrentUserId).orElseThrow();
            return UserPrincipal.create(persisted,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        });
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TenantContext.set(hotelMrId);
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
    @DisplayName("T1 - create() persiste le user dans le tenant courant avec hash BCrypt + role autorise")
    void shouldCreateUserInCurrentTenant() {
        DBUserCreateAdminDto dto = new DBUserCreateAdminDto(
                "bob.gerant", "bob@mr.test", "BobPwd123!Demo",
                "Bob", "Gerant", "+22244556677", "Reception", roleGerantId);

        DBUserAdminDto created = tx.execute(s -> hotelUserService.create(dto));

        assertNotNull(created);
        assertNotNull(created.userId());
        assertEquals("bob.gerant", created.username());
        assertEquals(hotelMrId, created.hotelId(),
                "Le user doit etre rattache au tenant courant (TenantContext)");
        assertEquals("GERANT", created.roleCode());

        DBUser fromDb = userRepository.findById(created.userId()).orElseThrow();
        assertTrue(PasswordUtil.verifyPassword("BobPwd123!Demo", fromDb.getPasswordHash()),
                "Le hash BCrypt doit verifier le clair");
    }

    @Test
    @DisplayName("T2 - create() refuse roleCode SUPERADMIN/ADMIN (anti-escalation)")
    void shouldRejectCreatingAdminRole() {
        DBUserCreateAdminDto escalation = new DBUserCreateAdminDto(
                "evil.admin", "evil@mr.test", "EvilPwd1!Demo",
                "Evil", "Admin", null, null, roleAdminId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> hotelUserService.create(escalation)));
        assertEquals("error.user.role.escalation.forbidden", ex.getMessage(),
                "Un ADMIN ne peut pas creer un autre ADMIN");

        // Aucun user evil.admin ne doit etre en BDD
        assertTrue(userRepository.findByUsername("evil.admin").isEmpty(),
                "Le user escalatoire ne doit pas avoir ete persiste");
    }

    @Test
    @DisplayName("T3 - update() refuse l'auto-modification (error.user.self.action.forbidden)")
    void shouldRejectSelfUpdate() {
        DBUserUpdateAdminDto patch = new DBUserUpdateAdminDto(
                null, "Hacked", null, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(s -> hotelUserService.update(adminCurrentUserId, patch)));
        assertEquals("error.user.self.action.forbidden", ex.getMessage());

        // L'admin doit etre intact en BDD
        DBUser stillAdmin = userRepository.findById(adminCurrentUserId).orElseThrow();
        assertEquals("Admin", stillAdmin.getPrenom(),
                "L'ADMIN courant ne doit pas avoir ete modifie");
    }

    @Test
    @DisplayName("T4 - findById() cross-hotel -> 404 ResourceNotFoundException (anti information leak)")
    void shouldRejectCrossHotelAccess() {
        // Cree un user dans hotel FR (autre tenant)
        Hotel frPersisted = hotelRepository.findById(hotelFrId).orElseThrow();
        Role gerantPersisted = roleRepository.findById(roleGerantId).orElseThrow();
        DBUser frUser = new DBUser();
        frUser.setUsername("foreign.user");
        frUser.setEmail("foreign@fr.test");
        frUser.setPasswordHash(PasswordUtil.hashPassword("Pwd123456!"));
        frUser.setPrenom("Foreign");
        frUser.setNom("User");
        frUser.setHotel(frPersisted);
        frUser.setRole(gerantPersisted);
        frUser.setActif(Boolean.TRUE);
        frUser.setCompteVerrouille(Boolean.FALSE);
        frUser.setTentativesConnexion(0);
        Long foreignUserId = userRepository.saveAndFlush(frUser).getUserId();

        // L'ADMIN courant (tenant MR) tente d'acceder a un user de FR
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> hotelUserService.findById(foreignUserId));
        assertEquals("error.user.notFound", ex.getMessage(),
                "Doit retourner 404 (pas 403) pour ne pas leak l'existence du user");
    }
}
