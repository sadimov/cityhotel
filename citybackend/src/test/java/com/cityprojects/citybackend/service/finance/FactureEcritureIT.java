package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT B3 - flux complet facture : creation, emission (auto -&gt; ecriture VTE),
 * annulation (contre-passation).
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - emission facture genere ecriture VTE liee (D=C, lignes correctes).</li>
 *   <li>T2 - annulation facture emise contre-passe l'ecriture
 *       (originale CONTRE_PASSEE, nouvelle VALIDEE inversee).</li>
 *   <li>T3 - isolation cross-tenant : ecriture creee en A non visible depuis B.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class FactureEcritureIT {

    @Autowired
    private FactureService factureService;

    @Autowired
    private EcritureComptableService ecritureComptableService;

    @Autowired
    private EcritureComptableRepository ecritureRepository;

    @Autowired
    private PlanComptableGeneralRepository pcgRepository;

    @Autowired
    private JournalComptableRepository journalRepository;

    @Autowired
    private ClientRepository clientRepository;

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
    private Long clientAId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        // PCG : seed comptes essentiels pour B3.
        savePcg("411100", "Clients particuliers", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("411200", "Clients societes", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("706100", "Ventes nuitees", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);
        savePcg("706200", "Ventes restauration", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);
        savePcg("706800", "Ventes autres services", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);

        // Hotels A et B
        Hotel a = new Hotel("HA", "Hotel A");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();
        Hotel b = new Hotel("HB", "Hotel B");
        b.setCodePays("MR");
        hotelBId = hotelRepository.saveAndFlush(b).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userA = newUser("a-user", "a@h.test", a, gerant);
        userB = newUser("b-user", "b@h.test", b, gerant);

        // Seed journal VTE pour chaque tenant
        seedJournal(hotelAId, "VTE", TypeJournal.VENTE);
        seedJournal(hotelBId, "VTE", TypeJournal.VENTE);

        // Client A (tenant)
        TenantScope.runAs(hotelAId, () -> {
            Client c = new Client();
            c.setNumeroClient("CLI-001");
            c.setPrenom("Sidi");
            c.setNom("Mohamed");
            c.setActif(Boolean.TRUE);
            clientAId = clientRepository.saveAndFlush(c).getClientId();
        });
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
        jdbcTemplate.update("DELETE FROM finance.audit_finance_log");
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
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void savePcg(String code, String libelle, int classe, NatureCompte nature, SensNormal sensNormal) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(sensNormal);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        pcgRepository.saveAndFlush(p);
    }

    private void seedJournal(Long hotelId, String code, TypeJournal type) {
        TenantScope.runAs(hotelId, () -> {
            JournalComptable j = new JournalComptable();
            j.setCode(code);
            j.setLibelle(type.name());
            j.setType(type);
            j.setActif(Boolean.TRUE);
            journalRepository.saveAndFlush(j);
        });
    }

    private DBUser newUser(String username, String email, Hotel hotel, Role role) {
        DBUser user = new DBUser(username, email, "$2a$12$placeholder",
                "Test", "User", hotel, role);
        user.setActif(Boolean.TRUE);
        user.setCompteVerrouille(Boolean.FALSE);
        return userRepository.saveAndFlush(user);
    }

    private void authenticateAs(DBUser user) {
        com.cityprojects.citybackend.security.UserPrincipal principal =
                com.cityprojects.citybackend.security.UserPrincipal.create(user,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ====================================================================
    // T1 - emission facture genere ecriture VTE liee
    // ====================================================================
    @Test
    @DisplayName("T1 - emission facture -> ecriture VTE 411100 D / 706100 C, lien stocke")
    void shouldGenerateVteOnEmission() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);

        // Cree une facture BROUILLON avec 1 ligne NUITEE de 100 MRU
        FactureCreateDto createDto = new FactureCreateDto(
                null, null, clientAId, null, null, null,
                LocalDate.now(), null, "MRU", null,
                List.of(new LigneFactureCreateDto(
                        TypeLigneFacture.NUITEE, null, null, null, null,
                        "Nuit test", BigDecimal.ONE, new BigDecimal("100.00"),
                        BigDecimal.ZERO, null)));
        FactureDto facture = transactionTemplate.execute(t -> factureService.create(createDto));
        assertNotNull(facture);
        assertEquals(StatutFacture.BROUILLON, facture.statut());

        // Emission - doit generer une ecriture
        FactureDto emise = transactionTemplate.execute(t -> factureService.emettre(facture.factureId()));
        assertEquals(StatutFacture.EMISE, emise.statut());

        // Recharge l'entite pour avoir ecritureEmissionId (le DTO ne l'expose pas)
        Long ecritureId = transactionTemplate.execute(t -> {
            EcritureComptable e = ecritureRepository.findAll().get(0);
            return e.getId();
        });
        assertNotNull(ecritureId);

        // Verifie l'ecriture
        transactionTemplate.execute(t -> {
            EcritureComptable e = ecritureRepository.findById(ecritureId).orElseThrow();
            assertEquals(StatutEcriture.VALIDEE, e.getStatut());
            assertEquals("VTE", e.getJournal().getCode());
            assertEquals(0, e.getTotalDebit().compareTo(new BigDecimal("100.00")));
            assertEquals(0, e.getTotalCredit().compareTo(new BigDecimal("100.00")));
            List<LigneEcriture> lignes = e.getLignes();
            assertEquals(2, lignes.size());
            // Lignes triees par ordre. Premiere = DEBIT 411100, deuxieme = CREDIT 706100
            assertEquals("411100", lignes.get(0).getCompteCode());
            assertEquals(SensLigne.DEBIT, lignes.get(0).getSens());
            assertEquals("706100", lignes.get(1).getCompteCode());
            assertEquals(SensLigne.CREDIT, lignes.get(1).getSens());
            return null;
        });
    }

    // ====================================================================
    // T2 - annulation contre-passe l'ecriture
    // ====================================================================
    @Test
    @DisplayName("T2 - annulation facture emise contre-passe l'ecriture")
    void shouldContrePasserOnAnnulation() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);

        FactureCreateDto createDto = new FactureCreateDto(
                null, null, clientAId, null, null, null,
                LocalDate.now(), null, "MRU", null,
                List.of(new LigneFactureCreateDto(
                        TypeLigneFacture.NUITEE, null, null, null, null,
                        "Nuit test", BigDecimal.ONE, new BigDecimal("75.00"),
                        BigDecimal.ZERO, null)));
        FactureDto facture = transactionTemplate.execute(t -> factureService.create(createDto));
        FactureDto emise = transactionTemplate.execute(t -> factureService.emettre(facture.factureId()));

        // Annulation - doit contre-passer
        FactureDto annulee = transactionTemplate.execute(
                t -> factureService.annuler(emise.factureId()));
        assertEquals(StatutFacture.ANNULEE, annulee.statut());

        // Verifier : 2 ecritures en base (originale CONTRE_PASSEE + nouvelle VALIDEE)
        transactionTemplate.execute(t -> {
            List<EcritureComptable> all = ecritureRepository.findAll();
            assertEquals(2, all.size());
            long contrePasseeCount = all.stream()
                    .filter(e -> e.getStatut() == StatutEcriture.CONTRE_PASSEE).count();
            long validéeCount = all.stream()
                    .filter(e -> e.getStatut() == StatutEcriture.VALIDEE).count();
            assertEquals(1, contrePasseeCount);
            assertEquals(1, validéeCount);
            return null;
        });
    }

    // ====================================================================
    // T3 - isolation multi-tenant
    // ====================================================================
    @Test
    @DisplayName("T3 - ecriture creee en A non visible depuis B")
    void shouldIsolateEcritureCrossTenant() {
        // Cree une facture emise sur A
        TenantContext.set(hotelAId);
        authenticateAs(userA);
        FactureCreateDto dto = new FactureCreateDto(
                null, null, clientAId, null, null, null,
                LocalDate.now(), null, "MRU", null,
                List.of(new LigneFactureCreateDto(
                        TypeLigneFacture.NUITEE, null, null, null, null,
                        "Nuit", BigDecimal.ONE, new BigDecimal("50.00"),
                        BigDecimal.ZERO, null)));
        FactureDto facture = transactionTemplate.execute(t -> factureService.create(dto));
        transactionTemplate.execute(t -> factureService.emettre(facture.factureId()));

        Long ecritureAId = transactionTemplate.execute(t ->
                ecritureRepository.findAll().get(0).getId());
        assertNotNull(ecritureAId);

        SecurityContextHolder.clearContext();
        TenantContext.clear();

        // Depuis B : tentative de lecture
        TenantContext.set(hotelBId);
        authenticateAs(userB);
        assertThrows(ResourceNotFoundException.class, () ->
                transactionTemplate.execute(t -> ecritureComptableService.findById(ecritureAId)));
    }
}
