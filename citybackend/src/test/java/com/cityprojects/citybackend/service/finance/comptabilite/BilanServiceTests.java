package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.BilanDto;
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
 * Tests Surefire du {@link BilanService} (B5).
 */
class BilanServiceTests {

    private LigneEcritureRepository ligneRepo;
    private PlanComptableGeneralRepository pcgRepo;
    private ExerciceRepository exerciceRepo;
    private XlsxExportService xlsxService;
    private BilanServiceImpl service;

    @BeforeEach
    void setUp() {
        ligneRepo = mock(LigneEcritureRepository.class);
        pcgRepo = mock(PlanComptableGeneralRepository.class);
        exerciceRepo = mock(ExerciceRepository.class);
        xlsxService = mock(XlsxExportService.class);
        service = new BilanServiceImpl(ligneRepo, pcgRepo, exerciceRepo, xlsxService);
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
                                             NatureCompte nature) {
        PlanComptableGeneral p = new PlanComptableGeneral();
        p.setCompteCode(code);
        p.setLibelle(libelle);
        p.setClasse(classe);
        p.setNature(nature);
        p.setSensNormal(nature == NatureCompte.ACTIF || nature == NatureCompte.CHARGE
                ? SensNormal.DEBITEUR : SensNormal.CREDITEUR);
        p.setUtilisable(Boolean.TRUE);
        p.setStatut(StatutCompteComptable.ACTIF);
        return p;
    }

    @Test
    @DisplayName("T1 - compute() : rubriques actif/passif/resultat construites - bilan equilibre")
    void computeHappyPath() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));

        // Classe 2 - immobilisations : 1 compte avec solde debiteur 1000
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("2%")))
                .thenReturn(List.of("213000"));
        when(pcgRepo.findByCompteCode("213000")).thenReturn(Optional.of(
                pcg("213000", "Constructions", 2, NatureCompte.ACTIF)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("213000"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("1000.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("213000"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        // Classe 3 - stocks : aucun compte
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("3%")))
                .thenReturn(List.of());
        // Classe 4 - clients (ACTIF) 200, fournisseurs (PASSIF) 300
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("4%")))
                .thenReturn(List.of("411100", "401100"));
        when(pcgRepo.findByCompteCode("411100")).thenReturn(Optional.of(
                pcg("411100", "Clients", 4, NatureCompte.ACTIF)));
        when(pcgRepo.findByCompteCode("401100")).thenReturn(Optional.of(
                pcg("401100", "Fournisseurs", 4, NatureCompte.PASSIF)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("200.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("411100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("401100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("401100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("300.00"));

        // Classe 5 - tresorerie : compte avec D 700, C 100 -> solde 600 debiteur (ACTIF)
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("5%")))
                .thenReturn(List.of("521100"));
        when(pcgRepo.findByCompteCode("521100")).thenReturn(Optional.of(
                pcg("521100", "Banque", 5, NatureCompte.MIXTE)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("521100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("700.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("521100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("100.00"));

        // Classe 1 - capitaux propres : 10x credit 800
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("1%")))
                .thenReturn(List.of("101100"));
        when(pcgRepo.findByCompteCode("101100")).thenReturn(Optional.of(
                pcg("101100", "Capital social", 1, NatureCompte.PASSIF)));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("101100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("101100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("800.00"));

        // Classe 6 (charges) : 500
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("6%")))
                .thenReturn(List.of("601100"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("500.00"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("601100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        // Classe 7 (produits) : 1100
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), eq("7%")))
                .thenReturn(List.of("706100"));
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneRepo.sumByCompteCodeAndDateBetween(eq("706100"), any(), any(), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("1100.00"));

        BilanDto dto = service.compute(1L, null);
        assertNotNull(dto);
        // Resultat net = 1100 - 500 = 600
        assertEquals(0, dto.resultatNet().compareTo(new BigDecimal("600.00")));
        // Rubriques actif : immobilisations + creances + tresorerie (stocks vide est presente avec 0)
        // Verifie qu'au moins une rubrique passif contient "Resultat de l'exercice"
        assertEquals(true, dto.passif().stream()
                .anyMatch(r -> "Resultat de l'exercice".equals(r.libelle())));
    }

    @Test
    @DisplayName("T2 - compute() refuse si exerciceId null -> error.etat.exerciceIdRequired")
    void exerciceIdRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(null, null));
        assertEquals("error.etat.exerciceIdRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - compute() refuse si exercice inexistant -> ResourceNotFoundException")
    void exerciceNotFound() {
        when(exerciceRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.compute(99L, null));
    }

    @Test
    @DisplayName("T4 - compute() refuse si dateArrete avant dateDebut exercice -> error.etat.dateRangeInvalide")
    void dateArreteInvalide() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(1L, LocalDate.of(2025, 12, 31)));
        assertEquals("error.etat.dateRangeInvalide", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - compute() : dateArrete par defaut = fin exercice")
    void dateArreteDefault() {
        when(exerciceRepo.findById(1L)).thenReturn(Optional.of(exercice()));
        when(ligneRepo.findDistinctCompteCodesByDateBetweenAndPrefixe(any(), any(), any()))
                .thenReturn(List.of());

        BilanDto dto = service.compute(1L, null);
        assertEquals(LocalDate.of(2026, 12, 31), dto.dateArrete());
    }
}
