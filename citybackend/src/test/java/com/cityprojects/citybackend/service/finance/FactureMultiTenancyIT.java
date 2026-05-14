package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT multi-tenant strict pour le module finance (Bloc B1 - fix audit L1).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code findAll} : tenant A ne voit que ses factures, jamais
 *       celles de tenant B.</li>
 *   <li>T2 - {@code findById} cross-tenant : facture creee en A, lue en B,
 *       renvoie {@link ResourceNotFoundException} (filtre Hibernate
 *       {@code @TenantId} natif).</li>
 *   <li>T3 - {@code LigneFactureRepository.existsByNuiteeId} : tenant A
 *       cree une ligne avec nuiteeId=42, tenant B avec ce meme tenant
 *       ne voit PAS cette ligne (la garde @TenantId portee par
 *       LigneFacture bouche la fuite signalee par l'audit du 2026-05-08).</li>
 * </ol>
 *
 * <h3>Strategie</h3>
 * <p>Pattern conforme au modele {@code FactureServiceTests}. Pas de
 * {@code @Transactional} sur les methodes ; {@link TransactionTemplate} pour
 * controler precisement les sessions Hibernate (le resolver de tenant
 * s'appelle a l'ouverture de session). Cleanup brut SQL en {@link #tearDown()}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureMultiTenancyIT {

    @Autowired
    private FactureService factureService;

    @Autowired
    private LigneFactureRepository ligneFactureRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DBUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelAId;
    private Long hotelBId;
    private DBUser userA;
    private DBUser userB;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        // Cleanup ordonne (compta-native B1 inclut compte_mapping, exercice, plan_comptable_general)
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.compte_mapping");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        Hotel a = new Hotel("MTA", "Hotel A");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();

        Hotel b = new Hotel("MTB", "Hotel B");
        b.setCodePays("MR");
        hotelBId = hotelRepository.saveAndFlush(b).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));

        userA = new DBUser("a-gerant", "a@h.test", "$2a$12$placeholder",
                "Sidi", "AAA", a, gerant);
        userA.setActif(Boolean.TRUE);
        userA.setCompteVerrouille(Boolean.FALSE);
        userA = userRepository.saveAndFlush(userA);

        userB = new DBUser("b-gerant", "b@h.test", "$2a$12$placeholder",
                "Fatma", "BBB", b, gerant);
        userB.setActif(Boolean.TRUE);
        userB.setCompteVerrouille(Boolean.FALSE);
        userB = userRepository.saveAndFlush(userB);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.compte_mapping");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void authenticateAs(DBUser user, String role) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - findAll : tenant A ne voit que ses factures")
    void findAllIsTenantScoped() {
        // Cree 2 factures pour hotel A
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        FactureDto a1 = transactionTemplate.execute(t -> factureService.create(
                emptyFactureDto()));
        FactureDto a2 = transactionTemplate.execute(t -> factureService.create(
                emptyFactureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Cree 1 facture pour hotel B
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        FactureDto b1 = transactionTemplate.execute(t -> factureService.create(
                emptyFactureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Lecture cote tenant A : 2 factures seulement
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        var pageA = transactionTemplate.execute(t -> factureService.findAll(PageRequest.of(0, 50)));
        assertNotNull(pageA);
        assertEquals(2, pageA.getTotalElements(),
                "Tenant A ne doit voir que ses 2 factures, jamais celle de B");
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Lecture cote tenant B : 1 facture
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        var pageB = transactionTemplate.execute(t -> factureService.findAll(PageRequest.of(0, 50)));
        assertEquals(1, pageB.getTotalElements(),
                "Tenant B ne doit voir que sa propre facture");
        assertEquals(b1.factureId(), pageB.getContent().get(0).factureId());
    }

    @Test
    @DisplayName("T2 - findById cross-tenant -> ResourceNotFoundException")
    void findByIdCrossTenantThrows404() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        FactureDto a1 = transactionTemplate.execute(t -> factureService.create(
                emptyFactureDto()));
        Long factureA = a1.factureId();
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenter de lire la facture de A depuis B
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> factureService.findById(factureA)));
    }

    @Test
    @DisplayName("T3 - LigneFactureRepository.existsByNuiteeId est tenant-scoped (audit L1 bouche)")
    void existsByNuiteeIdIsTenantScoped() {
        // Tenant A : cree une facture avec une ligne portant nuiteeId=4242
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        LigneFactureCreateDto ligneA = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Test nuitee 4242", BigDecimal.ONE,
                BigDecimal.valueOf(100), BigDecimal.ZERO, null);
        // On utilise DIVERS car nuiteeId pointerait vers hebergement.nuitees
        // qui n'existe pas dans ce contexte de test minimal. Mais on assigne
        // manuellement le nuiteeId via JDBC apres creation (pour simuler la
        // situation problematique decrite par l'audit L1).
        FactureDto a1 = transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null,
                        null, null, null, null, List.of(ligneA))));
        // Force le nuiteeId sur la ligne creee
        Long ligneId = a1.lignes().get(0).ligneFactureId();
        jdbcTemplate.update("UPDATE finance.lignes_factures SET nuitee_id = 4242 WHERE ligne_facture_id = ?",
                ligneId);
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenant A : voit bien sa ligne
        TenantContext.set(hotelAId);
        boolean fromA = transactionTemplate.execute(t -> ligneFactureRepository.existsByNuiteeId(4242L));
        assertTrue(fromA, "Tenant A doit voir sa propre ligne avec nuiteeId=4242");
        TenantContext.clear();

        // Tenant B : ne doit PAS voir la ligne creee par A
        TenantContext.set(hotelBId);
        boolean fromB = transactionTemplate.execute(t -> ligneFactureRepository.existsByNuiteeId(4242L));
        assertEquals(false, fromB,
                "Tenant B ne doit JAMAIS voir une ligne du tenant A (audit L1 : @TenantId sur LigneFacture)");
        TenantContext.clear();
    }

    /** Factory commune : facture vide sans FK ni ligne, sur la date du jour. */
    private static FactureCreateDto emptyFactureDto() {
        return new FactureCreateDto(null, null, null, null, null, null,
                null, null, null, null, List.of());
    }
}
