package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.AffectationPaiementRepository;
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

/**
 * IT multi-tenant strict pour le module paiement (Bloc B1 - fix audit L1).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code findAll} paiements : tenant A ne voit que ses paiements.</li>
 *   <li>T2 - {@code findById} cross-tenant : paiement A lu depuis B -&gt;
 *       {@link ResourceNotFoundException}.</li>
 *   <li>T3 - {@code AffectationPaiementRepository.sumMontantByLigneFactureId} :
 *       tenant A cree une affectation sur sa ligne, tenant B avec le meme ID
 *       de ligne ne voit aucune affectation (audit L1 : @TenantId sur
 *       AffectationPaiement bouche la fuite signalee).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementMultiTenancyIT {

    @Autowired
    private FactureService factureService;

    @Autowired
    private PaiementService paiementService;

    @Autowired
    private AffectationPaiementRepository affectationPaiementRepository;

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

        Hotel a = new Hotel("MTPA", "Hotel PA");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();
        Hotel b = new Hotel("MTPB", "Hotel PB");
        b.setCodePays("MR");
        hotelBId = hotelRepository.saveAndFlush(b).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userA = new DBUser("pa-gerant", "pa@h.test", "$2a$12$placeholder",
                "PA", "AA", a, gerant);
        userA.setActif(Boolean.TRUE); userA.setCompteVerrouille(Boolean.FALSE);
        userA = userRepository.saveAndFlush(userA);
        userB = new DBUser("pb-gerant", "pb@h.test", "$2a$12$placeholder",
                "PB", "BB", b, gerant);
        userB.setActif(Boolean.TRUE); userB.setCompteVerrouille(Boolean.FALSE);
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

    private PaiementDto createPaiement(Long tenantId, DBUser user, BigDecimal montant) {
        TenantContext.set(tenantId);
        authenticateAs(user, "GERANT");
        try {
            return transactionTemplate.execute(t -> paiementService.create(
                    new PaiementCreateDto(null, null, montant,
                            "MRU", ModePaiement.ESPECES, null, null, null)));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T1 - findAll paiements : tenant A ne voit que ses paiements")
    void findAllPaiementsIsTenantScoped() {
        createPaiement(hotelAId, userA, BigDecimal.valueOf(10000));
        createPaiement(hotelAId, userA, BigDecimal.valueOf(20000));
        PaiementDto p3 = createPaiement(hotelBId, userB, BigDecimal.valueOf(5000));

        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        var pageA = transactionTemplate.execute(t -> paiementService.findAll(PageRequest.of(0, 50)));
        assertEquals(2, pageA.getTotalElements(),
                "Tenant A ne doit voir que ses 2 paiements");
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        var pageB = transactionTemplate.execute(t -> paiementService.findAll(PageRequest.of(0, 50)));
        assertEquals(1, pageB.getTotalElements(), "Tenant B ne doit voir que son paiement");
        assertEquals(p3.paiementId(), pageB.getContent().get(0).paiementId());
    }

    @Test
    @DisplayName("T2 - findById cross-tenant -> ResourceNotFoundException")
    void findByIdCrossTenant404() {
        PaiementDto pA = createPaiement(hotelAId, userA, BigDecimal.valueOf(10000));

        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        assertThrows(ResourceNotFoundException.class,
                () -> transactionTemplate.execute(t -> paiementService.findById(pA.paiementId())));
    }

    @Test
    @DisplayName("T3 - sumMontantByLigneFactureId tenant-scoped (audit L1 sur AffectationPaiement)")
    void sumAffectationsTenantScoped() {
        // Tenant A : cree une facture + ligne + paiement + affectation sur la ligne
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        LigneFactureCreateDto ligneA = new LigneFactureCreateDto(
                TypeLigneFacture.DIVERS, null, null, null, null,
                "Test", BigDecimal.ONE, BigDecimal.valueOf(1000), BigDecimal.ZERO, null);
        FactureDto facA = transactionTemplate.execute(t -> factureService.create(
                new FactureCreateDto(null, null, null, null, null, null,
                        null, null, null, null, List.of(ligneA))));
        FactureDto emiseA = transactionTemplate.execute(t -> factureService.emettre(facA.factureId()));
        Long ligneAId = emiseA.lignes().get(0).ligneFactureId();

        PaiementDto payA = transactionTemplate.execute(t -> paiementService.create(
                new PaiementCreateDto(null, null, BigDecimal.valueOf(1000),
                        "MRU", ModePaiement.ESPECES, null, null, null)));
        // Affectation sur la ligne specifique
        transactionTemplate.execute(t -> paiementService.affecter(payA.paiementId(),
                List.of(new AffectationCreateDto(emiseA.factureId(), ligneAId, BigDecimal.valueOf(1000)))));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenant A voit son affectation (1000)
        TenantContext.set(hotelAId);
        BigDecimal sumA = transactionTemplate.execute(
                t -> affectationPaiementRepository.sumMontantByLigneFactureId(ligneAId));
        assertNotNull(sumA);
        assertEquals(0, sumA.compareTo(BigDecimal.valueOf(1000)),
                "Tenant A doit voir son affectation de 1000");
        TenantContext.clear();

        // Tenant B avec le MEME ligneId : doit voir 0 (audit L1 bouche)
        TenantContext.set(hotelBId);
        BigDecimal sumB = transactionTemplate.execute(
                t -> affectationPaiementRepository.sumMontantByLigneFactureId(ligneAId));
        // COALESCE => 0
        assertEquals(0, sumB.compareTo(BigDecimal.ZERO),
                "Tenant B ne doit JAMAIS voir une affectation du tenant A (audit L1)");
        TenantContext.clear();
    }
}
