package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.BonSortie;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito) du {@link EcritureGenerationService}.
 *
 * <p>Couvre :</p>
 * <ol>
 *   <li>T1 - emettreEcritureFacture() : ventilation correcte par type ligne,
 *       D = C, journal VTE, reference = numeroFacture.</li>
 *   <li>T2 - emettreEcritureFacture() : skip si TTC = 0.</li>
 *   <li>T3 - emettreEcritureEncaissement() ESPECES : journal CAI, compte 531100 D,
 *       411xxx C.</li>
 *   <li>T4 - emettreEcritureEncaissement() CHEQUE : journal BAN, compte 512100 D.</li>
 *   <li>T5 - emettreEcritureEncaissement() societeId fourni : compte 411200 (societe).</li>
 *   <li>T6 - emettreEcritureReceptionBC() : journal ACH, 311 D / 401 C.</li>
 *   <li>T7 - emettreEcritureSortieBS() : journal OD, 601 D / 311 C.</li>
 *   <li>T8 - mode degrade : PCG vide -&gt; skip silencieux (retour null).</li>
 * </ol>
 */
class EcritureGenerationServiceTests {

    private EcritureComptableService ecritureService;
    private CompteMappingService mappingService;
    private LigneFactureRepository ligneFactureRepository;
    private PlanComptableGeneralRepository pcgRepository;
    private JournalComptableRepository journalRepository;
    private TauxTvaConfigService tauxTvaConfigService;
    private EcritureGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        ecritureService = mock(EcritureComptableService.class);
        mappingService = mock(CompteMappingService.class);
        ligneFactureRepository = mock(LigneFactureRepository.class);
        pcgRepository = mock(PlanComptableGeneralRepository.class);
        journalRepository = mock(JournalComptableRepository.class);
        tauxTvaConfigService = mock(TauxTvaConfigService.class);

        // Par defaut : PCG seede (count > 0) et journaux presents.
        when(pcgRepository.count()).thenReturn(50L);
        when(journalRepository.findByCode(any())).thenReturn(
                Optional.of(new com.cityprojects.citybackend.entity.finance.JournalComptable()));

        // Comptes par defaut : on retourne le defaultCompteCode() de l'enum.
        when(mappingService.getCompte(any(TypeEvenementComptable.class)))
                .thenAnswer(inv -> ((TypeEvenementComptable) inv.getArgument(0)).defaultCompteCode());

        // B4 : par defaut on ne configure pas de TVA -> taux 0 partout
        // pour conserver les assertions B3 sur les ecritures sans TVA.
        // Chaque test qui veut tester la TVA mockera explicitement.
        when(tauxTvaConfigService.getTaux(any(TypeServiceTva.class)))
                .thenReturn(BigDecimal.ZERO);

        // Service "creer" renvoie un DTO synthetique avec id 100.
        when(ecritureService.creer(any(EcritureComptableCreateDto.class))).thenAnswer(inv -> {
            EcritureComptableCreateDto dto = inv.getArgument(0);
            return new EcritureComptableDto(
                    100L,
                    "JRN-" + dto.journalCode() + "-2026-MR-000001",
                    dto.dateComptable(),
                    dto.datePiece(),
                    1L, dto.journalCode(), "Test",
                    1L, "2026",
                    dto.libelle(), dto.reference(),
                    StatutEcriture.VALIDEE,
                    null, null,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                    List.of(),
                    null, null);
        });

        service = new EcritureGenerationServiceImpl(
                ecritureService, mappingService, ligneFactureRepository,
                pcgRepository, journalRepository, tauxTvaConfigService);
    }

    @Test
    @DisplayName("T1 - facture VTE : ventilation par type ligne (NUITEE -> 706100, SERVICE -> 706800)")
    void shouldGenerateVteWithProductDispatch() {
        Facture facture = new Facture();
        facture.setFactureId(1L);
        facture.setNumeroFacture("FACT-2026-MR-000001");
        facture.setClientId(7L);
        facture.setTypeFacture(TypeFacture.FACTURE);
        facture.setDateFacture(LocalDate.now());
        facture.setMontantTtc(new BigDecimal("150.00"));

        LigneFacture l1 = new LigneFacture();
        l1.setLigneFactureId(11L);
        l1.setFactureId(1L);
        l1.setTypeLigne(TypeLigneFacture.NUITEE);
        l1.setQuantite(BigDecimal.ONE);
        l1.setPrixUnitaire(new BigDecimal("100.00"));
        l1.setTauxTva(BigDecimal.ZERO);
        l1.setMontantHt(new BigDecimal("100.00"));
        l1.setMontantTva(BigDecimal.ZERO);
        l1.setMontantTtc(new BigDecimal("100.00"));

        LigneFacture l2 = new LigneFacture();
        l2.setLigneFactureId(12L);
        l2.setFactureId(1L);
        l2.setTypeLigne(TypeLigneFacture.SERVICE);
        l2.setQuantite(BigDecimal.ONE);
        l2.setPrixUnitaire(new BigDecimal("50.00"));
        l2.setTauxTva(BigDecimal.ZERO);
        l2.setMontantHt(new BigDecimal("50.00"));
        l2.setMontantTva(BigDecimal.ZERO);
        l2.setMontantTtc(new BigDecimal("50.00"));

        when(ligneFactureRepository.findByFactureIdOrderByLigneFactureIdAsc(1L))
                .thenReturn(List.of(l1, l2));

        Long ecritureId = service.emettreEcritureFacture(facture);
        assertEquals(100L, ecritureId);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("VTE", dto.journalCode());
        assertEquals("FACT-2026-MR-000001", dto.reference());

        // 1 debit (411xxx, 150) + 2 credits (706100=100, 706800=50)
        assertEquals(3, dto.lignes().size());
        LigneEcritureCreateDto debit = dto.lignes().get(0);
        assertEquals(SensLigne.DEBIT, debit.sens());
        assertEquals("411100", debit.compteCode());
        assertEquals(0, debit.montant().compareTo(new BigDecimal("150.00")));

        // Verifie equilibre : Σ D = Σ C
        BigDecimal totalC = BigDecimal.ZERO;
        for (LigneEcritureCreateDto l : dto.lignes()) {
            if (l.sens() == SensLigne.CREDIT) totalC = totalC.add(l.montant());
        }
        assertEquals(0, totalC.compareTo(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("T2 - facture sans montant -> skip (null)")
    void shouldSkipIfNoAmount() {
        Facture facture = new Facture();
        facture.setFactureId(2L);
        facture.setNumeroFacture("FACT-X");
        facture.setMontantTtc(BigDecimal.ZERO);
        Long id = service.emettreEcritureFacture(facture);
        assertNull(id);
        verify(ecritureService, never()).creer(any());
    }

    @Test
    @DisplayName("T3 - paiement ESPECES : journal CAI, 531100 DEBIT / 411xxx CREDIT")
    void shouldGenerateCaiForEspeces() {
        Paiement p = paiementBase(ModePaiement.ESPECES, new BigDecimal("200.00"));
        Long id = service.emettreEcritureEncaissement(p, 7L, null);
        assertEquals(100L, id);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("CAI", dto.journalCode());
        assertEquals("PAY-2026-MR-000001", dto.reference());
        LigneEcritureCreateDto debit = dto.lignes().get(0);
        assertEquals("531100", debit.compteCode());
        assertEquals(SensLigne.DEBIT, debit.sens());
        LigneEcritureCreateDto credit = dto.lignes().get(1);
        assertEquals("411100", credit.compteCode());
        assertEquals(SensLigne.CREDIT, credit.sens());
    }

    @Test
    @DisplayName("T4 - paiement CHEQUE : journal BAN, 512100 DEBIT")
    void shouldGenerateBanForCheque() {
        Paiement p = paiementBase(ModePaiement.CHEQUE, new BigDecimal("80.00"));
        service.emettreEcritureEncaissement(p, 7L, null);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("BAN", dto.journalCode());
        assertEquals("512100", dto.lignes().get(0).compteCode());
    }

    @Test
    @DisplayName("T5 - paiement avec societeId : credit sur 411200 (CLIENT_SOCIETE)")
    void shouldUseSocieteAccountWhenSocieteIdProvided() {
        Paiement p = paiementBase(ModePaiement.VIREMENT, new BigDecimal("5000.00"));
        service.emettreEcritureEncaissement(p, null, 42L);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("BAN", dto.journalCode());
        assertEquals("411200", dto.lignes().get(1).compteCode()); // CLIENT_SOCIETE
    }

    @Test
    @DisplayName("T6 - reception BC sans TVA : journal ACH, 311 DEBIT / 401 CREDIT")
    void shouldGenerateAchForBC() {
        BonCommande bc = new BonCommande();
        bc.setBonCommandeId(99L);
        bc.setNumeroBc("BC-2026-MR-000001");
        Long id = service.emettreEcritureReceptionBC(bc, new BigDecimal("750.00"));
        assertEquals(100L, id);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("ACH", dto.journalCode());
        // Tva = 0 -> 2 lignes seulement (D 311 / C 401)
        assertEquals(2, dto.lignes().size());
        assertEquals("311000", dto.lignes().get(0).compteCode());
        assertEquals(SensLigne.DEBIT, dto.lignes().get(0).sens());
        assertEquals(0, dto.lignes().get(0).montant().compareTo(new BigDecimal("750.00")));
        assertEquals("401100", dto.lignes().get(1).compteCode());
        assertEquals(SensLigne.CREDIT, dto.lignes().get(1).sens());
        assertEquals(0, dto.lignes().get(1).montant().compareTo(new BigDecimal("750.00")));
    }

    @Test
    @DisplayName("T6bis - reception BC avec TVA 16% : 311 D / 445600 D / 401 C")
    void shouldGenerateAchForBCWithTva() {
        when(tauxTvaConfigService.getTaux(TypeServiceTva.ACHAT_MARCHANDISES))
                .thenReturn(new BigDecimal("16.00"));

        BonCommande bc = new BonCommande();
        bc.setBonCommandeId(101L);
        bc.setNumeroBc("BC-2026-MR-000002");
        Long id = service.emettreEcritureReceptionBC(bc, new BigDecimal("100.00"));
        assertEquals(100L, id);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("ACH", dto.journalCode());
        // HT 100 + TVA 16 = TTC 116
        assertEquals(3, dto.lignes().size());
        assertEquals("311000", dto.lignes().get(0).compteCode());
        assertEquals(SensLigne.DEBIT, dto.lignes().get(0).sens());
        assertEquals(0, dto.lignes().get(0).montant().compareTo(new BigDecimal("100.00")));
        assertEquals("445600", dto.lignes().get(1).compteCode());
        assertEquals(SensLigne.DEBIT, dto.lignes().get(1).sens());
        assertEquals(0, dto.lignes().get(1).montant().compareTo(new BigDecimal("16.00")));
        assertEquals("401100", dto.lignes().get(2).compteCode());
        assertEquals(SensLigne.CREDIT, dto.lignes().get(2).sens());
        assertEquals(0, dto.lignes().get(2).montant().compareTo(new BigDecimal("116.00")));
    }

    @Test
    @DisplayName("T7 - sortie BS : journal OD, 601 DEBIT / 311 CREDIT")
    void shouldGenerateOdForBS() {
        BonSortie bs = new BonSortie();
        bs.setBonSortieId(33L);
        bs.setNumeroBs("BS-2026-MR-000007");
        service.emettreEcritureSortieBS(bs, new BigDecimal("12.50"));

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureService).creer(cap.capture());
        EcritureComptableCreateDto dto = cap.getValue();
        assertEquals("OD", dto.journalCode());
        assertEquals("601000", dto.lignes().get(0).compteCode());
        assertEquals(SensLigne.DEBIT, dto.lignes().get(0).sens());
        assertEquals("311000", dto.lignes().get(1).compteCode());
    }

    @Test
    @DisplayName("T8 - mode degrade : PCG vide -> aucune ecriture creee")
    void shouldSkipWhenPcgEmpty() {
        when(pcgRepository.count()).thenReturn(0L);
        Facture facture = new Facture();
        facture.setFactureId(50L);
        facture.setNumeroFacture("FACT-Y");
        facture.setMontantTtc(new BigDecimal("100.00"));
        facture.setClientId(1L);
        facture.setTypeFacture(TypeFacture.FACTURE);
        Long id = service.emettreEcritureFacture(facture);
        assertNull(id);
        verify(ecritureService, never()).creer(any());
    }

    @Test
    @DisplayName("T9 - mode degrade : journal absent -> aucune ecriture creee")
    void shouldSkipWhenJournalAbsent() {
        when(journalRepository.findByCode("ACH")).thenReturn(Optional.empty());
        BonCommande bc = new BonCommande();
        bc.setBonCommandeId(60L);
        bc.setNumeroBc("BC-Z");
        Long id = service.emettreEcritureReceptionBC(bc, new BigDecimal("99.00"));
        assertNull(id);
        verify(ecritureService, never()).creer(any());
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private static Paiement paiementBase(ModePaiement mode, BigDecimal montant) {
        Paiement p = new Paiement();
        p.setPaiementId(50L);
        p.setNumeroPaiement("PAY-2026-MR-000001");
        p.setMontantTotal(montant);
        p.setModePaiement(mode);
        p.setStatut(StatutPaiement.VALIDE);
        p.setDatePaiement(LocalDate.now());
        return p;
    }
}
