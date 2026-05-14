package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.dto.inventory.BonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.CategorieProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.FournisseurCreateDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionBonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionLigneDto;
import com.cityprojects.citybackend.entity.core.DBUser;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.core.Role;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import com.cityprojects.citybackend.repository.core.DBUserRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.core.RoleRepository;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.inventory.CategorieProduitService;
import com.cityprojects.citybackend.service.inventory.FournisseurService;
import com.cityprojects.citybackend.service.inventory.ProduitService;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT B3 - reception bon de commande genere une ecriture ACH
 * (311000 DEBIT / 401100 CREDIT, journal ACH).
 */
@SpringBootTest
@ActiveProfiles("test")
class BonCommandeReceptionEcritureIT {

    @Autowired private BonCommandeService bonCommandeService;
    @Autowired private FournisseurService fournisseurService;
    @Autowired private CategorieProduitService categorieService;
    @Autowired private ProduitService produitService;
    @Autowired private EcritureComptableRepository ecritureRepository;
    @Autowired private PlanComptableGeneralRepository pcgRepository;
    @Autowired private JournalComptableRepository journalRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DBUserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long hotelAId;
    private DBUser userA;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        cleanAll();

        savePcg("311000", "Stock marchandises", 3, NatureCompte.ACTIF, SensNormal.DEBITEUR);
        savePcg("401100", "Fournisseurs ordinaires", 4, NatureCompte.PASSIF, SensNormal.CREDITEUR);
        // B4 : TVA deductible. Le test n'instancie pas TauxTvaConfig pour
        // l'hotel A -> fallback sur defaut ACHAT_MARCHANDISES = 16% ->
        // l'ecriture ACH ajoute une ligne 445600 DEBIT.
        savePcg("445600", "TVA deductible", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR);

        Hotel a = new Hotel("HA", "Hotel A");
        a.setCodePays("MR");
        hotelAId = hotelRepository.saveAndFlush(a).getHotelId();
        Role mag = roleRepository.saveAndFlush(new Role("MAGASIN", "Magasin"));
        userA = new DBUser("magasin", "m@h.test", "$2a$12$placeholder",
                "Mag", "Asin", a, mag);
        userA.setActif(Boolean.TRUE);
        userA.setCompteVerrouille(Boolean.FALSE);
        userA = userRepository.saveAndFlush(userA);

        seedJournal(hotelAId, "ACH", TypeJournal.ACHAT);
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
        jdbcTemplate.update("DELETE FROM inventory.mouvements_stock");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.bons_commande");
        jdbcTemplate.update("DELETE FROM inventory.lignes_bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.bons_sortie");
        jdbcTemplate.update("DELETE FROM inventory.produits");
        jdbcTemplate.update("DELETE FROM inventory.categories_produits");
        jdbcTemplate.update("DELETE FROM inventory.fournisseurs");
        jdbcTemplate.update("DELETE FROM finance.exercice");
        jdbcTemplate.update("DELETE FROM finance.numerotation_sequence");
        jdbcTemplate.update("DELETE FROM finance.plan_comptable_general");
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

    private void authenticateAs(DBUser user) {
        UserPrincipal principal = UserPrincipal.create(user, Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_MAGASIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("T1 - reception BC -> ecriture ACH 311 DEBIT / 401 CREDIT")
    void shouldGenerateAchOnReception() {
        TenantContext.set(hotelAId);
        authenticateAs(userA);

        // 1) Cree fournisseur, categorie, produit
        Long fournisseurId = transactionTemplate.execute(t ->
                fournisseurService.create(new FournisseurCreateDto(
                        "Fournisseur Test", "Contact",
                        "+22200000000", "f@t.test", null, null, null, null))
                        .fournisseurId());
        Long categorieId = transactionTemplate.execute(t ->
                categorieService.create(new CategorieProduitCreateDto("CAT-A", "Categorie A", null))
                        .categorieId());
        Long produitId = transactionTemplate.execute(t ->
                produitService.create(new ProduitCreateDto(
                        "PROD-A", "Produit A", null,
                        categorieId, "PCE", new BigDecimal("10.00"),
                        0, 0, fournisseurId, Boolean.TRUE))
                        .produitId());

        // 2) Cree BC + ligne (10 unites x 10.00 = 100.00)
        BonCommandeDto bcDto = transactionTemplate.execute(t ->
                bonCommandeService.create(new BonCommandeCreateDto(
                        fournisseurId, null, null,
                        List.of(new LigneBonCommandeCreateDto(produitId, 10, new BigDecimal("10.00"))))));

        // 3) Confirme BC (BROUILLON -> ENVOYE -> CONFIRME)
        transactionTemplate.execute(t ->
                bonCommandeService.changerStatut(bcDto.bonCommandeId(), StatutBonCommande.ENVOYE));
        transactionTemplate.execute(t ->
                bonCommandeService.changerStatut(bcDto.bonCommandeId(), StatutBonCommande.CONFIRME));

        // 4) Reception complete : 10 unites
        transactionTemplate.execute(t ->
                bonCommandeService.receptionner(bcDto.bonCommandeId(),
                        new ReceptionBonCommandeDto(
                                List.of(new ReceptionLigneDto(
                                        bcDto.lignes().get(0).ligneId(), 10)))));

        // 5) Verifie l'ecriture ACH (B4 : HT 100 + TVA 16 = TTC 116)
        transactionTemplate.execute(t -> {
            List<EcritureComptable> all = ecritureRepository.findAll();
            assertEquals(1, all.size(), "1 seule ecriture attendue (la reception)");
            EcritureComptable ach = all.get(0);
            assertEquals("ACH", ach.getJournal().getCode());
            assertEquals(StatutEcriture.VALIDEE, ach.getStatut());
            // HT + TVA cote DEBIT == TTC cote CREDIT == 116
            assertEquals(0, ach.getTotalDebit().compareTo(new BigDecimal("116.00")));
            assertEquals(0, ach.getTotalCredit().compareTo(new BigDecimal("116.00")));
            assertEquals(3, ach.getLignes().size());
            // Ordre : 311 D (HT) / 445600 D (TVA) / 401100 C (TTC)
            assertEquals("311000", ach.getLignes().get(0).getCompteCode());
            assertEquals(SensLigne.DEBIT, ach.getLignes().get(0).getSens());
            assertEquals(0, ach.getLignes().get(0).getMontant().compareTo(new BigDecimal("100.00")));
            assertEquals("445600", ach.getLignes().get(1).getCompteCode());
            assertEquals(SensLigne.DEBIT, ach.getLignes().get(1).getSens());
            assertEquals(0, ach.getLignes().get(1).getMontant().compareTo(new BigDecimal("16.00")));
            assertEquals("401100", ach.getLignes().get(2).getCompteCode());
            assertEquals(SensLigne.CREDIT, ach.getLignes().get(2).getSens());
            assertEquals(0, ach.getLignes().get(2).getMontant().compareTo(new BigDecimal("116.00")));
            return null;
        });
    }
}
