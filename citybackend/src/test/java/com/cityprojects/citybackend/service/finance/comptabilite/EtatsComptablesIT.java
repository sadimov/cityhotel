package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceComptableDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalEditionDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalFilterDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.EcritureComptableService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT end-to-end multi-tenant strict pour les etats comptables OHADA (B5).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - Flux complet : 3 ecritures reelles cree dans hotel A, calcul de
 *       la balance, soldes coherents (Sigma D = Sigma C).</li>
 *   <li>T2 - Isolation tenant : hotel A et hotel B ont des ecritures, la
 *       balance d'A ne contient pas celles de B.</li>
 *   <li>T3 - Grand livre : report initial + solde progressif fonctionnels.</li>
 *   <li>T4 - Edition de journal : seules les ecritures du tenant courant sont
 *       listees.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class EtatsComptablesIT {

    @Autowired private EcritureComptableService ecritureService;
    @Autowired private BalanceComptableService balanceService;
    @Autowired private GrandLivreService grandLivreService;
    @Autowired private JournalEditionService journalEditionService;

    @Autowired private JournalComptableRepository journalRepository;
    @Autowired private PlanComptableGeneralRepository pcgRepository;
    @Autowired private com.cityprojects.citybackend.repository.finance.ExerciceRepository exerciceRepositoryForTest;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tt;
    private Long hotelAId;
    private Long hotelBId;
    private DBUser userA;
    private DBUser userB;
    private Long journalAId;

    @BeforeEach
    void setUp() {
        tt = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        savePcg("411100", "Clients", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("521100", "Banque", 5, NatureCompte.MIXTE, SensNormal.MIXTE);
        savePcg("601100", "Achats", 6, NatureCompte.CHARGE, SensNormal.DEBITEUR);
        savePcg("706110", "Ventes nuitees", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);

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

        journalAId = seedJournalVte(hotelAId);
        seedJournalVte(hotelBId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();
    }

    private void cleanAll() {
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

    private void savePcg(String code, String libelle, int classe, NatureCompte nature, SensNormal sens) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(sens);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        pcgRepository.saveAndFlush(p);
    }

    private Long seedJournalVte(Long hotelId) {
        TenantContext.set(hotelId);
        try {
            JournalComptable j = new JournalComptable();
            j.setCode("VTE");
            j.setLibelle("Ventes");
            j.setType(TypeJournal.VENTE);
            j.setActif(Boolean.TRUE);
            return journalRepository.saveAndFlush(j).getId();
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

    private static EcritureComptableCreateDto venteDto(LocalDate date, String montant) {
        return new EcritureComptableCreateDto(
                date, date, "VTE",
                "Vente facturee " + date,
                "FACT-" + date,
                List.of(
                        new LigneEcritureCreateDto(null, "411100", null, SensLigne.DEBIT,
                                new BigDecimal(montant), null),
                        new LigneEcritureCreateDto(null, "706110", null, SensLigne.CREDIT,
                                new BigDecimal(montant), null)));
    }

    @Test
    @DisplayName("T1 - flux complet : 3 ecritures hotel A, balance equilibree, soldes corrects")
    void balanceFromRealEcritures() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        try {
            tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now().minusDays(2), "100.00")));
            tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now().minusDays(1), "200.00")));
            tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "300.00")));

            BalanceComptableDto bal = tt.execute(t -> balanceService.compute(
                    new BalanceFilterDto(null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(1), null)));
            assertNotNull(bal);
            assertEquals(2, bal.lignes().size());
            // Sigma soldeD == Sigma soldeC (balance equilibree par principe partie double)
            assertEquals(0, bal.totalSoldeDebiteur().compareTo(bal.totalSoldeCrediteur()));
            // Sigma debits = 600, Sigma credits = 600
            assertEquals(0, bal.totalDebit().compareTo(new BigDecimal("600.00")));
            assertEquals(0, bal.totalCredit().compareTo(new BigDecimal("600.00")));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T2 - multi-tenant : balance hotel A ne voit pas les ecritures hotel B")
    void balanceTenantIsolation() {
        // Hotel A : 1 ecriture 100
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "100.00")));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Hotel B : 1 ecriture 999
        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "999.00")));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Balance hotel A : doit etre exactement 100, jamais 999
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        try {
            BalanceComptableDto balA = tt.execute(t -> balanceService.compute(
                    new BalanceFilterDto(null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(1), null)));
            assertEquals(0, balA.totalDebit().compareTo(new BigDecimal("100.00")),
                    "Balance A doit etre 100, jamais 100+999 (sinon fuite tenant)");
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T3 - grand livre : report initial + solde progressif")
    void grandLivreReportInitial() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        try {
            tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now().minusDays(5), "500.00")));
            tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now().minusDays(1), "300.00")));

            // Recupere l'exerciceId courant (auto-cree par ExerciceService lors de la 1ere creation).
            // Le report initial n'est calcule que si l'exerciceId est fourni
            // (pour determiner la borne basse [dateDebutExercice, dateDebut - 1 jour]).
            Long currentExerciceId = tt.execute(t ->
                    exerciceRepositoryForTest.findContainingDate(LocalDate.now())
                            .map(com.cityprojects.citybackend.entity.finance.Exercice::getId)
                            .orElse(null));
            assertNotNull(currentExerciceId, "Exercice courant doit etre auto-cree par ExerciceService");

            // Grand livre limite a hier-aujourd'hui -> report initial doit valoir 500
            GrandLivreDto gl = tt.execute(t -> grandLivreService.compute(
                    new GrandLivreFilterDto("411100", currentExerciceId,
                            LocalDate.now().minusDays(2), LocalDate.now().plusDays(1))));
            assertEquals(1, gl.comptes().size());
            assertEquals(0, gl.comptes().get(0).reportInitial().compareTo(new BigDecimal("500.00")),
                    "Report initial = ecritures anterieures a dateDebut");
            // 1 ligne (l'ecriture de 300)
            assertEquals(1, gl.comptes().get(0).lignes().size());
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("T4 - journal : ne liste que les ecritures du tenant courant")
    void journalTenantIsolation() {
        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "100.00")));
        tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "200.00")));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        TenantContext.set(hotelBId);
        authenticateAs(userB, "GERANT");
        tt.execute(t -> ecritureService.creer(venteDto(LocalDate.now(), "999.00")));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        TenantContext.set(hotelAId);
        authenticateAs(userA, "GERANT");
        try {
            JournalEditionDto ed = tt.execute(t -> journalEditionService.compute(
                    new JournalFilterDto(journalAId,
                            LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
            assertEquals(2, ed.ecritures().size(),
                    "Journal A doit avoir 2 ecritures, jamais celle de B");
            assertEquals(0, ed.totalDebit().compareTo(new BigDecimal("300.00")));
            assertTrue(ed.ecritures().stream().noneMatch(e ->
                    e.lignes().stream().anyMatch(l -> l.debit().compareTo(new BigDecimal("999.00")) == 0
                            || l.credit().compareTo(new BigDecimal("999.00")) == 0)));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
