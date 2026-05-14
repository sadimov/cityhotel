package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.CompteGrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreFilterDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.LigneEcritureRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire du {@link GrandLivreService} (B5).
 */
class GrandLivreServiceTests {

    private LigneEcritureRepository ligneRepo;
    private PlanComptableGeneralRepository pcgRepo;
    private ExerciceRepository exerciceRepo;
    private XlsxExportService xlsxService;
    private GrandLivreServiceImpl service;

    @BeforeEach
    void setUp() {
        ligneRepo = mock(LigneEcritureRepository.class);
        pcgRepo = mock(PlanComptableGeneralRepository.class);
        exerciceRepo = mock(ExerciceRepository.class);
        xlsxService = mock(XlsxExportService.class);
        service = new GrandLivreServiceImpl(ligneRepo, pcgRepo, exerciceRepo, xlsxService);
    }

    private static Exercice exercice() {
        Exercice e = new Exercice();
        e.setId(1L);
        e.setCode("2026");
        e.setDateDebut(LocalDate.of(2026, 1, 1));
        e.setDateFin(LocalDate.of(2026, 12, 31));
        return e;
    }

    private static PlanComptableGeneral pcg(String code, String libelle) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(Integer.parseInt(code.substring(0, 1)));
        p.setNature(NatureCompte.ACTIF);
        p.setSensNormal(SensNormal.DEBITEUR);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        return p;
    }

    private static LigneEcriture ligne(EcritureComptable ec, String compteCode, SensLigne sens,
                                        String montant, int ordre) {
        LigneEcriture l = new LigneEcriture();
        l.setId((long) ordre);
        l.setOrdre(ordre);
        l.setCompteCode(compteCode);
        l.setSens(sens);
        l.setMontant(new BigDecimal(montant));
        l.setEcriture(ec);
        l.setLibelle("ligne " + ordre);
        return l;
    }

    private static EcritureComptable ecriture(Long id, LocalDate date, String numero) {
        EcritureComptable e = new EcritureComptable();
        e.setId(id);
        e.setDateComptable(date);
        e.setNumero(numero);
        e.setLibelle("ec " + numero);
        e.setReference("REF-" + numero);
        JournalComptable j = new JournalComptable();
        j.setCode("VTE");
        e.setJournal(j);
        return e;
    }

    @Test
    @DisplayName("T1 - compute() : report initial calcule + solde progressif correct")
    void computeReportInitialEtSoldeProgressif() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(pcg("411100", "Clients")));

        // dateDebut = 1er mars, dateFin = 31 mars 2026
        // Report initial = D-C sur [2026-01-01, 2026-02-28]
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 2, 28)),
                eq(SensLigne.DEBIT))).thenReturn(new BigDecimal("500.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 2, 28)),
                eq(SensLigne.CREDIT))).thenReturn(new BigDecimal("100.00"));

        // Sur mars : 2 lignes
        EcritureComptable ec1 = ecriture(1L, LocalDate.of(2026, 3, 5), "JRN-VTE-2026-MR-000001");
        EcritureComptable ec2 = ecriture(2L, LocalDate.of(2026, 3, 10), "JRN-VTE-2026-MR-000002");
        when(ligneRepo.findByCompteCodeAndDateBetween(eq("411100"),
                eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(List.of(
                        ligne(ec1, "411100", SensLigne.DEBIT, "200.00", 1),
                        ligne(ec2, "411100", SensLigne.CREDIT, "150.00", 1)));

        GrandLivreDto dto = service.compute(new GrandLivreFilterDto("411100", 1L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertNotNull(dto);
        assertEquals(1, dto.comptes().size());
        CompteGrandLivreDto c = dto.comptes().get(0);
        // Report initial = 500-100 = 400
        assertEquals(0, c.reportInitial().compareTo(new BigDecimal("400.00")));
        // Solde progressif ligne 1 = 400 + 200 = 600
        assertEquals(0, c.lignes().get(0).soldeProgressif().compareTo(new BigDecimal("600.00")));
        // Solde progressif ligne 2 = 600 - 150 = 450
        assertEquals(0, c.lignes().get(1).soldeProgressif().compareTo(new BigDecimal("450.00")));
        // Solde final = 400 + 200 - 150 = 450
        assertEquals(0, c.soldeFinal().compareTo(new BigDecimal("450.00")));
    }

    @Test
    @DisplayName("T2 - compute() : compteCode null -> liste tous les comptes mouvementes")
    void computeAllComptesWhenCompteCodeNull() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetween(any(), any()))
                .thenReturn(List.of("411100", "706100"));
        when(pcgRepo.findByCompteCode(any())).thenReturn(Optional.empty());
        when(ligneRepo.sumByCompteCodeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.findByCompteCodeAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        GrandLivreDto dto = service.compute(new GrandLivreFilterDto(null, 1L, null, null));
        assertEquals(2, dto.comptes().size());
    }

    @Test
    @DisplayName("T3 - compute() : dateDebut == debutExercice -> report initial = 0")
    void reportInitialZeroIfDateDebutEqualsExerciceDebut() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(pcg("411100", "Clients")));
        when(ligneRepo.findByCompteCodeAndDateBetween(any(), any(), any())).thenReturn(List.of());

        GrandLivreDto dto = service.compute(new GrandLivreFilterDto("411100", 1L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        CompteGrandLivreDto c = dto.comptes().get(0);
        assertEquals(0, c.reportInitial().compareTo(BigDecimal.ZERO.setScale(2)));
    }

    @Test
    @DisplayName("T4 - compute() refuse si filter null -> error.etat.filterRequired")
    void filterNullRefuse() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(null));
        assertEquals("error.etat.filterRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - compute() : libelle de ligne fallback sur libelle ecriture si vide")
    void libelleFallback() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(pcg("411100", "Clients")));
        EcritureComptable ec = ecriture(1L, LocalDate.of(2026, 3, 5), "JRN-VTE-2026-MR-000001");
        LigneEcriture l = ligne(ec, "411100", SensLigne.DEBIT, "100.00", 1);
        l.setLibelle(null); // pas de libelle ligne -> fallback ecriture
        when(ligneRepo.findByCompteCodeAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(l));
        when(ligneRepo.sumByCompteCodeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        GrandLivreDto dto = service.compute(new GrandLivreFilterDto("411100", 1L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertEquals("ec JRN-VTE-2026-MR-000001", dto.comptes().get(0).lignes().get(0).libelleEcriture());
    }
}
