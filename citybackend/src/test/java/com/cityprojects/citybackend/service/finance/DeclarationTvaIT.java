package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.dto.finance.DeclarationTvaDto;
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
import com.cityprojects.citybackend.entity.finance.StatutDeclarationTva;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT B4 - flux complet :
 * <ol>
 *   <li>configuration TVA 16% sur RESTAURATION,</li>
 *   <li>creation facture avec ligne PRODUIT -&gt; TVA appliquee,</li>
 *   <li>emission -&gt; ecriture VTE inclut une ligne CREDIT 445700,</li>
 *   <li>calcul declaration TVA sur la periode,</li>
 *   <li>validation -&gt; ecriture liquidation OD generee, statut VALIDEE.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class DeclarationTvaIT {

    @Autowired private FactureService factureService;
    @Autowired private DeclarationTvaService declarationTvaService;
    @Autowired private TauxTvaConfigService tauxTvaConfigService;
    @Autowired private EcritureComptableRepository ecritureRepository;
    @Autowired private PlanComptableGeneralRepository pcgRepository;
    @Autowired private JournalComptableRepository journalRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long hotelAId;
    private Long hotelBId;
    private DBUser userA;
    private Long clientAId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        // PCG : tous les comptes utilises B4
        savePcg("411100", "Clients particuliers", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("706100", "Ventes nuitees", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);
        savePcg("706200", "Ventes restauration", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR);
        savePcg("445700", "TVA collectee", 4, NatureCompte.PASSIF, SensNormal.CREDITEUR);
        savePcg("445600", "TVA deductible", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("445800", "TVA a decaisser", 4, NatureCompte.PASSIF, SensNormal.CREDITEUR);

        Hotel a = new Hotel("HA", "Hotel A");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();
        Hotel b = new Hotel("HB", "Hotel B");
        b.setCodePays("MR");
        hotelBId = hotelRepository.saveAndFlush(b).getHotelId();

        Role gerant = roleRepository.saveAndFlush(new Role("GERANT", "Gerant"));
        userA = newUser("a-user", "a@h.test", a, gerant);
        newUser("b-user", "b@h.test", b, gerant);

        seedJournal(hotelAId, "VTE", TypeJournal.VENTE);
        seedJournal(hotelAId, "OD", TypeJournal.OPERATION_DIVERSE);
        seedJournal(hotelBId, "VTE", TypeJournal.VENTE);
        seedJournal(hotelBId, "OD", TypeJournal.OPERATION_DIVERSE);

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
        jdbcTemplate.update("DELETE FROM finance.declaration_tva");
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
        jdbcTemplate.update("DELETE FROM finance.taux_tva_config");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM finance.plan_comptable_general");
        jdbcTemplate.update("DELETE FROM client.clients");
        jdbcTemplate.update("DELETE FROM client.societes");
        jdbcTemplate.update("DELETE FROM core.dbusers");
        jdbcTemplate.update("DELETE FROM core.hotels");
        jdbcTemplate.update("DELETE FROM core.roles");
    }

    private void savePcg(String code, String libelle, int classe, NatureCompte nature, SensNormal sn) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(sn);
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
        UserPrincipal principal = UserPrincipal.create(user,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GERANT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - flux complet : facture TVA 16% -> declaration calcul -> validation -> liquidation OD")
    void shouldRunFullFlow() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);

        // 1) Configure RESTAURATION a 16% (l'hotel n'a pas encore de config seedee)
        tx.execute(t -> tauxTvaConfigService.update(
                TypeServiceTva.RESTAURATION, new BigDecimal("16.00"), null, null));

        // 2) Cree une facture PRODUIT (tauxTva null -> resolu via service)
        LocalDate today = LocalDate.now();
        FactureCreateDto createDto = new FactureCreateDto(
                null, null, clientAId, null, null, null,
                today, null, "MRU", null,
                List.of(new LigneFactureCreateDto(
                        TypeLigneFacture.PRODUIT, null, null, null, null,
                        "Cafe", BigDecimal.ONE, new BigDecimal("100.00"),
                        null /* taux resolu via service */, null)));
        FactureDto facture = tx.execute(t -> factureService.create(createDto));
        assertEquals(0, facture.montantTva().compareTo(new BigDecimal("16.00")));
        assertEquals(0, facture.montantTtc().compareTo(new BigDecimal("116.00")));

        // 3) Emission -> ecriture VTE avec ligne CREDIT 445700 = 16
        tx.execute(t -> factureService.emettre(facture.factureId()));
        tx.execute(t -> {
            List<EcritureComptable> ecritures = ecritureRepository.findAll();
            assertEquals(1, ecritures.size());
            EcritureComptable vte = ecritures.get(0);
            assertEquals("VTE", vte.getJournal().getCode());
            // 3 lignes : D 411100 116, C 706200 100, C 445700 16
            assertEquals(3, vte.getLignes().size());
            BigDecimal tvaCollectee = vte.getLignes().stream()
                    .filter(l -> "445700".equals(l.getCompteCode()))
                    .map(LigneEcriture::getMontant)
                    .findFirst().orElseThrow();
            assertEquals(0, tvaCollectee.compareTo(new BigDecimal("16.00")));
            return null;
        });

        // 4) Calcul declaration sur la periode du mois
        LocalDate debut = today.withDayOfMonth(1);
        LocalDate fin = today.withDayOfMonth(today.lengthOfMonth());
        DeclarationTvaDto decl = tx.execute(t -> declarationTvaService.calculer(debut, fin));
        assertNotNull(decl);
        assertEquals(StatutDeclarationTva.BROUILLON, decl.statut());
        assertEquals(0, decl.totalTvaCollectee().compareTo(new BigDecimal("16.00")));
        assertEquals(0, decl.totalTvaDeductible().compareTo(BigDecimal.ZERO));
        assertEquals(0, decl.totalTvaADecaisser().compareTo(new BigDecimal("16.00")));

        // 5) Calcul idempotent : meme periode -> meme id
        DeclarationTvaDto decl2 = tx.execute(t -> declarationTvaService.calculer(debut, fin));
        assertEquals(decl.id(), decl2.id());

        // 6) Validation -> ecriture liquidation OD
        DeclarationTvaDto validee = tx.execute(t -> declarationTvaService.valider(decl.id()));
        assertEquals(StatutDeclarationTva.VALIDEE, validee.statut());
        assertNotNull(validee.ecritureLiquidationId());
        assertNotNull(validee.dateValidation());

        tx.execute(t -> {
            List<EcritureComptable> all = ecritureRepository.findAll();
            assertEquals(2, all.size());  // VTE de l'emission + OD de la liquidation
            EcritureComptable liquidation = ecritureRepository.findById(validee.ecritureLiquidationId())
                    .orElseThrow();
            assertEquals("OD", liquidation.getJournal().getCode());
            assertEquals(StatutEcriture.VALIDEE, liquidation.getStatut());
            // 2 lignes : D 445700 16, C 445800 16 (pas de TVA deductible)
            assertEquals(2, liquidation.getLignes().size());
            assertEquals(0, liquidation.getTotalDebit().compareTo(new BigDecimal("16.00")));
            assertEquals(0, liquidation.getTotalCredit().compareTo(new BigDecimal("16.00")));
            // Solde sur 445800 cote CREDIT (a decaisser)
            boolean adecaisser = liquidation.getLignes().stream()
                    .anyMatch(l -> "445800".equals(l.getCompteCode())
                            && l.getSens() == SensLigne.CREDIT);
            assertTrue(adecaisser);
            return null;
        });

        // 7) Re-validation : refus
        assertThrows(BusinessException.class, () ->
                tx.execute(t -> declarationTvaService.valider(decl.id())));
    }

    @Test
    @DisplayName("T2 - declaration aucun montant -> refus de validation")
    void shouldRejectEmptyDeclaration() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);

        LocalDate debut = LocalDate.now().minusYears(1).withDayOfMonth(1);
        LocalDate fin = debut.withDayOfMonth(debut.lengthOfMonth());
        // Pas d'ecriture sur la periode -> declaration a 0
        DeclarationTvaDto decl = tx.execute(t -> declarationTvaService.calculer(debut, fin));
        assertEquals(0, decl.totalTvaCollectee().compareTo(BigDecimal.ZERO));
        assertEquals(0, decl.totalTvaDeductible().compareTo(BigDecimal.ZERO));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tx.execute(t -> declarationTvaService.valider(decl.id())));
        assertEquals("error.declaration.aucunMontant", ex.getMessage());
    }
}
