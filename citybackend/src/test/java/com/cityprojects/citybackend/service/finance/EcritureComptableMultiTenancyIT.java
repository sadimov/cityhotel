package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IT multi-tenant strict pour le moteur d'ecritures comptables (Bloc B2).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code findAll} : tenant A ne voit que ses ecritures.</li>
 *   <li>T2 - {@code findById} cross-tenant : ecriture creee en A, lue en B
 *       -&gt; {@link ResourceNotFoundException} (filtre Hibernate @TenantId).</li>
 *   <li>T3 - {@code numerotation} segmentee : tenant A et tenant B obtiennent
 *       chacun leur sequence {@code JRN-VTE-2026-{codePays}-000001} sans
 *       collision.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class EcritureComptableMultiTenancyIT {

    @Autowired
    private EcritureComptableService ecritureService;

    @Autowired
    private JournalComptableRepository journalRepository;

    @Autowired
    private PlanComptableGeneralRepository pcgRepository;

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

        // Cleanup ordonne : enfants -> parents.
        jdbcTemplate.update("DELETE FROM finance.ligne_ecriture");
        jdbcTemplate.update("DELETE FROM finance.ecriture_comptable");
        jdbcTemplate.update("DELETE FROM finance.journal_comptable");
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.compte_mapping");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM finance.plan_comptable_general");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");

        // Seed PCG minimaliste (411100 + 706100)
        savePcg("411100", "Clients particuliers");
        savePcg("706100", "Ventes - nuitees hebergement");

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

        // Cree un journal VTE pour chaque tenant
        seedJournalVte(hotelAId);
        seedJournalVte(hotelBId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM finance.ligne_ecriture");
        jdbcTemplate.update("DELETE FROM finance.ecriture_comptable");
        jdbcTemplate.update("DELETE FROM finance.journal_comptable");
        jdbcTemplate.update("DELETE FROM finance.affectations_paiements");
        jdbcTemplate.update("DELETE FROM finance.operations_comptes");
        jdbcTemplate.update("DELETE FROM finance.paiements");
        jdbcTemplate.update("DELETE FROM finance.lignes_factures");
        jdbcTemplate.update("DELETE FROM finance.factures");
        jdbcTemplate.update("DELETE FROM finance.comptes");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.compte_mapping");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM finance.plan_comptable_general");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void savePcg(String code, String libelle) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(code.startsWith("4") ? 4 : 7);
        p.setNature(code.startsWith("4")
                ? com.cityprojects.citybackend.entity.finance.NatureCompte.ACTIF
                : com.cityprojects.citybackend.entity.finance.NatureCompte.PRODUIT);
        p.setSensNormal(code.startsWith("4")
                ? com.cityprojects.citybackend.entity.finance.SensNormal.DEBITEUR
                : com.cityprojects.citybackend.entity.finance.SensNormal.CREDITEUR);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(com.cityprojects.citybackend.entity.finance.StatutCompteComptable.ACTIF);
        pcgRepository.saveAndFlush(p);
    }

    private void seedJournalVte(Long hotelId) {
        TenantContext.set(hotelId);
        try {
            JournalComptable j = new JournalComptable();
            j.setCode("VTE");
            j.setLibelle("Ventes");
            j.setType(TypeJournal.VENTE);
            j.setActif(Boolean.TRUE);
            journalRepository.saveAndFlush(j);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticateAs(DBUser user, String role) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static EcritureComptableCreateDto okEcritureDto() {
        return new EcritureComptableCreateDto(
                LocalDate.now(),
                LocalDate.now(),
                "VTE",
                "Vente test multi-tenant",
                null,
                List.of(
                        new LigneEcritureCreateDto(null, "411100", null, SensLigne.DEBIT,
                                new BigDecimal("100.00"), null),
                        new LigneEcritureCreateDto(null, "706100", null, SensLigne.CREDIT,
                                new BigDecimal("100.00"), null)
                ));
    }

    @Test
    @DisplayName("T1 - findAll : tenant A ne voit que ses ecritures")
    void findAllIsTenantScoped() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        transactionTemplate.execute(t -> ecritureService.creer(okEcritureDto()));
        transactionTemplate.execute(t -> ecritureService.creer(okEcritureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        transactionTemplate.execute(t -> ecritureService.creer(okEcritureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenant A doit voir 2 ecritures
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        var pageA = transactionTemplate.execute(
                t -> ecritureService.findAll(PageRequest.of(0, 50)));
        assertNotNull(pageA);
        assertEquals(2, pageA.getTotalElements(),
                "Tenant A ne doit voir que ses 2 ecritures, jamais celle de B");
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenant B doit voir 1 ecriture
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        var pageB = transactionTemplate.execute(
                t -> ecritureService.findAll(PageRequest.of(0, 50)));
        assertEquals(1, pageB.getTotalElements(),
                "Tenant B ne doit voir que sa propre ecriture");
    }

    @Test
    @DisplayName("T2 - findById cross-tenant -> ResourceNotFoundException")
    void findByIdCrossTenantThrows404() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        EcritureComptableDto cree = transactionTemplate.execute(
                t -> ecritureService.creer(okEcritureDto()));
        Long ecritureA = cree.id();
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Tenter de lire l'ecriture de A depuis B
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> ecritureService.findById(ecritureA)));
    }

    @Test
    @DisplayName("T3 - numerotation segmentee par tenant : chaque hotel a sa propre suite")
    void numerotationSegmenteeParHotel() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        EcritureComptableDto a1 = transactionTemplate.execute(
                t -> ecritureService.creer(okEcritureDto()));
        EcritureComptableDto a2 = transactionTemplate.execute(
                t -> ecritureService.creer(okEcritureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        EcritureComptableDto b1 = transactionTemplate.execute(
                t -> ecritureService.creer(okEcritureDto()));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Verifier que les sequences sont independantes : 1 et 2 pour A, 1 pour B
        int currentYear = LocalDate.now().getYear();
        assertEquals(String.format("JRN-VTE-%d-MR-000001", currentYear), a1.numero());
        assertEquals(String.format("JRN-VTE-%d-MR-000002", currentYear), a2.numero());
        assertEquals(String.format("JRN-VTE-%d-MR-000001", currentYear), b1.numero(),
                "Tenant B doit demarrer sa propre sequence a 000001 (segmentation par hotel)");
        // Statut VALIDEE
        assertEquals(StatutEcriture.VALIDEE, a1.statut());
        assertEquals(StatutEcriture.VALIDEE, b1.statut());
    }
}
