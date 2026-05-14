package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.CompteResultatDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire du {@link CompteResultatService} (B5).
 */
class CompteResultatServiceTests {

    private LigneEcritureRepository ligneRepo;
    private PlanComptableGeneralRepository pcgRepo;
    private ExerciceRepository exerciceRepo;
    private XlsxExportService xlsxService;
    private CompteResultatServiceImpl service;

    @BeforeEach
    void setUp() {
        ligneRepo = mock(LigneEcritureRepository.class);
        pcgRepo = mock(PlanComptableGeneralRepository.class);
        exerciceRepo = mock(ExerciceRepository.class);
        xlsxService = mock(XlsxExportService.class);
        service = new CompteResultatServiceImpl(ligneRepo, pcgRepo, exerciceRepo, xlsxService);
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
        p.setNature(code.startsWith("6") ? NatureCompte.CHARGE : NatureCompte.PRODUIT);
        p.setSensNormal(code.startsWith("6") ? SensNormal.DEBITEUR : SensNormal.CREDITEUR);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        return p;
    }

    @Test
    @DisplayName("T1 - compute() : produits et charges agreges - resultat net + marge brute corrects")
    void computeHappyPath() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));

        // Ventes hebergement (7061) : credit 1000
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("7061%")))
                .thenReturn(List.of("706110"));
        when(pcgRepo.findByCompteCode("706110")).thenReturn(Optional.of(pcg("706110", "Ventes nuitees")));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706110"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706110"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("1000.00"));

        // Achats consommes (601) : debit 400
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("601%")))
                .thenReturn(List.of("601100"));
        when(pcgRepo.findByCompteCode("601100")).thenReturn(Optional.of(pcg("601100", "Achats")));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("400.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        // Tous les autres prefixes : vides par defaut
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), any()))
                .thenReturn(List.of());
        // Re-stub ceux qu'on a configures explicitement (override pour any())
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("7061%")))
                .thenReturn(List.of("706110"));
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("601%")))
                .thenReturn(List.of("601100"));

        CompteResultatDto dto = service.compute(1L, null, null);
        assertNotNull(dto);
        // Total produits = 1000, charges = 400, resultat net = 600
        assertEquals(0, dto.totalProduits().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, dto.totalCharges().compareTo(new BigDecimal("400.00")));
        assertEquals(0, dto.resultatNet().compareTo(new BigDecimal("600.00")));
        // Marge brute = ventes (1000) - achats consommes (400) = 600
        assertEquals(0, dto.margeBrute().compareTo(new BigDecimal("600.00")));
    }

    @Test
    @DisplayName("T2 - compute() refuse si exerciceId null -> error.etat.exerciceIdRequired")
    void exerciceIdRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(null, null, null));
        assertEquals("error.etat.exerciceIdRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - compute() refuse si exercice inexistant -> ResourceNotFoundException")
    void exerciceNotFound() {
        when(exerciceRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                service.compute(99L, null, null));
    }

    @Test
    @DisplayName("T4 - compute() : dateDebut/dateFin par defaut = bornes exercice")
    void datesDefaultExercice() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), any()))
                .thenReturn(List.of());

        CompteResultatDto dto = service.compute(1L, null, null);
        assertEquals(LocalDate.of(2026, 1, 1), dto.dateDebut());
        assertEquals(LocalDate.of(2026, 12, 31), dto.dateFin());
    }

    @Test
    @DisplayName("T5 - compute() refuse si dateFin avant dateDebut -> error.etat.dateRangeInvalide")
    void dateRangeInvalide() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(1L, LocalDate.of(2026, 12, 31),
                        LocalDate.of(2026, 1, 1)));
        assertEquals("error.etat.dateRangeInvalide", ex.getMessage());
    }
}
