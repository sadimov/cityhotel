package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
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

/**
 * IT B3 - flux complet paiement : creation (genere ecriture CAI/BAN),
 * annulation (contre-passation).
 */
@SpringBootTest
@ActiveProfiles("test")
class PaiementEcritureIT {

    @Autowired private FactureService factureService;
    @Autowired private PaiementService paiementService;
    @Autowired private EcritureComptableRepository ecritureRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private PlanComptableGeneralRepository pcgRepository;
    @Autowired private JournalComptableRepository journalRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelAId;
    private DBUser userA;
    private Long clientAId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        // PCG : seed comptes essentiels
        savePcg("411100", "Clients particuliers", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("706100", "Ventes nuitees", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);
        savePcg("531100", "Caisse especes", 5, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("512100", "Banque", 5, NatureCompte.ACTIF, SensNormal.DEBITEUR);

        Hotel a = new Hotel("HA", "Hotel A");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();
        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userA = new DBUser("a-user", "a@h.test", "$2a$12$placeholder",
                "Test", "User", a, gerant);
        userA.setActif(Boolean.TRUE);
        userA.setCompteVerrouille(Boolean.FALSE);
        userA = userRepository.saveAndFlush(userA);

        seedJournal(hotelAId, "VTE", TypeJournal.VENTE);
        seedJournal(hotelAId, "CAI", TypeJournal.TRESORERIE);
        seedJournal(hotelAId, "BAN", TypeJournal.TRESORERIE);

        TenantScope.runAs(hotelAId, () -> {
            Client c = new Client();
            c.setNumeroClient("CLI-1");
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

    private void authenticateAs(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private FactureDto createEmittedFacture(BigDecimal montant) {
        FactureCreateDto dto = new FactureCreateDto(
                null, null, clientAId, null, null, null,
                LocalDate.now(), null, "MRU", null,
                List.of(new LigneFactureCreateDto(
                        TypeLigneFacture.NUITEE, null, null, null, null,
                        "Nuit", BigDecimal.ONE, montant,
                        BigDecimal.ZERO, null)));
        FactureDto created = transactionTemplate.execute(t -> factureService.create(dto));
        return transactionTemplate.execute(t -> factureService.emettre(created.factureId()));
    }

    @Test
    @DisplayName("T1 - paiement ESPECES sur facture -> ecriture CAI 531100 D / 411100 C")
    void shouldGenerateCaiOnEspecesPayment() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);
        FactureDto facture = createEmittedFacture(new BigDecimal("100.00"));

        // Compte existant en base : nombre d'ecritures avant paiement
        int avantPayment = transactionTemplate.execute(t -> ecritureRepository.findAll().size());
        assertEquals(1, avantPayment); // 1 ecriture VTE (de l'emission)

        PaiementCreateDto dto = new PaiementCreateDto(
                null, facture.factureId(), new BigDecimal("100.00"),
                "MRU", ModePaiement.ESPECES, "ref-001",
                LocalDate.now(), "Paiement test");
        PaiementDto paiement = transactionTemplate.execute(t -> paiementService.create(dto));
        assertNotNull(paiement);
        assertEquals(StatutPaiement.VALIDE, paiement.statut());

        // 2 ecritures attendues : VTE (emission) + CAI (encaissement)
        transactionTemplate.execute(t -> {
            List<EcritureComptable> all = ecritureRepository.findAll();
            assertEquals(2, all.size());
            EcritureComptable cai = all.stream()
                    .filter(e -> "CAI".equals(e.getJournal().getCode()))
                    .findFirst().orElseThrow();
            assertEquals(StatutEcriture.VALIDEE, cai.getStatut());
            assertEquals(0, cai.getTotalDebit().compareTo(new BigDecimal("100.00")));
            assertEquals(2, cai.getLignes().size());
            assertEquals("531100", cai.getLignes().get(0).getCompteCode());
            assertEquals(SensLigne.DEBIT, cai.getLignes().get(0).getSens());
            assertEquals("411100", cai.getLignes().get(1).getCompteCode());
            assertEquals(SensLigne.CREDIT, cai.getLignes().get(1).getSens());
            return null;
        });
    }

    @Test
    @DisplayName("T2 - annulation paiement -> contre-passation de l'ecriture d'encaissement")
    void shouldContrePasserOnPaymentAnnulation() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);
        FactureDto facture = createEmittedFacture(new BigDecimal("50.00"));

        // Cree un paiement non affecte (sans factureId pour pouvoir annuler)
        PaiementCreateDto dto = new PaiementCreateDto(
                null, null, new BigDecimal("50.00"),
                "MRU", ModePaiement.ESPECES, "ref-002",
                LocalDate.now(), "Acompte");
        PaiementDto paiement = transactionTemplate.execute(t -> paiementService.create(dto));

        // Annulation - doit contre-passer
        PaiementDto annule = transactionTemplate.execute(
                t -> paiementService.annuler(paiement.paiementId()));
        assertEquals(StatutPaiement.ANNULE, annule.statut());

        // 3 ecritures attendues : VTE (emission facture) + CAI (encaissement) + contre-passation CAI
        transactionTemplate.execute(t -> {
            List<EcritureComptable> all = ecritureRepository.findAll();
            assertEquals(3, all.size());
            long cpCount = all.stream()
                    .filter(e -> e.getStatut() == StatutEcriture.CONTRE_PASSEE).count();
            long valCount = all.stream()
                    .filter(e -> e.getStatut() == StatutEcriture.VALIDEE).count();
            assertEquals(1, cpCount, "1 ecriture doit etre CONTRE_PASSEE (l'ancien CAI)");
            assertEquals(2, valCount, "2 ecritures restent VALIDEE (VTE emission + nouvelle CP)");
            return null;
        });
    }
}
