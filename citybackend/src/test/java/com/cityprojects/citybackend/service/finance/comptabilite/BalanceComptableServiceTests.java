package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceComptableDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneBalanceDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.SensNormal;
import com.cityprojects.citybackend.entity.finance.StatutCompteComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire du {@link BalanceComptableService} (B5).
 */
class BalanceComptableServiceTests {

    private LigneEcritureRepository ligneRepo;
    private PlanComptableGeneralRepository pcgRepo;
    private ExerciceRepository exerciceRepo;
    private XlsxExportService xlsxService;
    private BalanceComptableServiceImpl service;

    @BeforeEach
    void setUp() {
        ligneRepo = mock(LigneEcritureRepository.class);
        pcgRepo = mock(PlanComptableGeneralRepository.class);
        exerciceRepo = mock(ExerciceRepository.class);
        xlsxService = mock(XlsxExportService.class);
        service = new BalanceComptableServiceImpl(ligneRepo, pcgRepo, exerciceRepo, xlsxService);
    }

    private static Exercice exercice() {
        Exercice e = new Exercice();
        e.setId(1L);
        e.setCode("2026");
        e.setDateDebut(LocalDate.of(2026, 1, 1));
        e.setDateFin(LocalDate.of(2026, 12, 31));
        return e;
    }

    private static PlanComptableGeneral pcg(String code, String libelle, int classe,
                                              NatureCompte nature, SensNormal sens) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(sens);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        return p;
    }

    @Test
    @DisplayName("T1 - compute() : 2 comptes, balance equilibree, soldes corrects par sens normal")
    void computeHappyPath() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetween(any(), any()))
                .thenReturn(List.of("411100", "706100"));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(
                pcg("411100", "Clients", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR)));
        when(pcgRepo.findByCompteCode("706100")).thenReturn(Optional.of(
                pcg("706100", "Ventes", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR)));
        // Client : debit 1000, credit 200 -> solde debiteur 800
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("1000.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("200.00"));
        // Ventes : debit 0, credit 800 -> solde crediteur 800
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("800.00"));

        BalanceComptableDto dto = service.compute(new BalanceFilterDto(1L, null, null, null));
        assertNotNull(dto);
        assertEquals(2, dto.lignes().size());
        LigneBalanceDto l1 = dto.lignes().get(0);
        assertEquals("411100", l1.compteCode());
        assertEquals(0, l1.soldeDebiteur().compareTo(new BigDecimal("800.00")));
        assertEquals(0, l1.soldeCrediteur().compareTo(BigDecimal.ZERO.setScale(2)));
        LigneBalanceDto l2 = dto.lignes().get(1);
        assertEquals("706100", l2.compteCode());
        assertEquals(0, l2.soldeDebiteur().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, l2.soldeCrediteur().compareTo(new BigDecimal("800.00")));
        // Equilibre : Sigma D = Sigma C
        assertEquals(0, dto.totalSoldeDebiteur().compareTo(dto.totalSoldeCrediteur()));
    }

    @Test
    @DisplayName("T2 - compute() : compte MIXTE (tresorerie) - solde debiteur si D > C")
    void computeMixteDebiteur() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetween(any(), any()))
                .thenReturn(List.of("521100"));
        when(pcgRepo.findByCompteCode("521100")).thenReturn(Optional.of(
                pcg("521100", "Banque", 5, NatureCompte.MIXTE, SensNormal.MIXTE)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("521100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("1500.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("521100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("500.00"));

        BalanceComptableDto dto = service.compute(new BalanceFilterDto(1L, null, null, null));
        LigneBalanceDto l = dto.lignes().get(0);
        assertEquals(0, l.soldeDebiteur().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, l.soldeCrediteur().compareTo(BigDecimal.ZERO.setScale(2)));
    }

    @Test
    @DisplayName("T3 - compute() : filtre par classe applique le prefixe LIKE")
    void computeWithClasseFilter() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("6%")))
                .thenReturn(List.of("601100"));
        when(pcgRepo.findByCompteCode("601100")).thenReturn(Optional.of(
                pcg("601100", "Achats", 6, NatureCompte.CHARGE, SensNormal.DEBITEUR)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("300.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        BalanceComptableDto dto = service.compute(new BalanceFilterDto(1L, null, null, 6));
        assertEquals(1, dto.lignes().size());
        assertEquals(6, dto.lignes().get(0).classe());
    }

    @Test
    @DisplayName("T4 - compute() refuse si exerciceId inexistant -> ResourceNotFoundException")
    void computeExerciceNotFound() {
        when(exerciceRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                service.compute(new BalanceFilterDto(99L, null, null, null)));
    }

    @Test
    @DisplayName("T5 - compute() refuse si ni exerciceId ni dates -> error.etat.filterRequired")
    void computeFilterRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(new BalanceFilterDto(null, null, null, null)));
        assertEquals("error.etat.filterRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T6 - compute() refuse si classe hors 1-7 -> error.etat.classeInvalide")
    void computeClasseInvalide() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(new BalanceFilterDto(1L, null, null, 9)));
        assertEquals("error.etat.classeInvalide", ex.getMessage());
    }

    @Test
    @DisplayName("T7 - compute() : equilibre - Sigma soldeD == Sigma soldeC sur balance complete")
    void computeEquilibreVerifie() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetween(any(), any()))
                .thenReturn(List.of("411100", "706100"));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(
                pcg("411100", "Clients", 4, NatureCompte.ACTIF, SensNormal.DEBITEUR)));
        when(pcgRepo.findByCompteCode("706100")).thenReturn(Optional.of(
                pcg("706100", "Ventes", 7, NatureCompte.PRODUIT, SensNormal.CREDITEUR)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("500.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("500.00"));
        BalanceComptableDto dto = service.compute(new BalanceFilterDto(1L, null, null, null));
        assertTrue(dto.totalSoldeDebiteur().compareTo(dto.totalSoldeCrediteur()) == 0);
    }

    @Test
    @DisplayName("T8 - computeSoldes() : helper statique - DEBITEUR avec D > C -> solde D")
    void computeSoldesHelper() {
        BigDecimal[] r = BalanceComptableServiceImpl.computeSoldes(
                new BigDecimal("100"), new BigDecimal("40"), SensNormal.DEBITEUR);
        assertEquals(0, r[0].compareTo(new BigDecimal("60")));
        assertEquals(0, r[1].compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("T9 - compute() : dateFin avant dateDebut -> error.etat.dateRangeInvalide")
    void computeDateRangeInvalide() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(new BalanceFilterDto(null,
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 1, 1), null)));
        assertEquals("error.etat.dateRangeInvalide", ex.getMessage());
    }
}
