package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.DeclarationTvaDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.DeclarationTva;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutDeclarationTva;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.DeclarationTvaRepository;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.LigneEcritureRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito) du {@link DeclarationTvaService} B4.
 */
class DeclarationTvaServiceTests {

    private DeclarationTvaRepository repository;
    private LigneEcritureRepository ligneEcritureRepository;
    private ExerciceRepository exerciceRepository;
    private CompteMappingService compteMappingService;
    private EcritureComptableService ecritureComptableService;
    private DeclarationTvaServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(DeclarationTvaRepository.class);
        ligneEcritureRepository = mock(LigneEcritureRepository.class);
        exerciceRepository = mock(ExerciceRepository.class);
        compteMappingService = mock(CompteMappingService.class);
        ecritureComptableService = mock(EcritureComptableService.class);

        // Comptes par defaut
        when(compteMappingService.getCompte(TypeEvenementComptable.TVA_COLLECTEE))
                .thenReturn("445700");
        when(compteMappingService.getCompte(TypeEvenementComptable.TVA_DEDUCTIBLE))
                .thenReturn("445600");
        when(compteMappingService.getCompte(TypeEvenementComptable.TVA_A_DECAISSER))
                .thenReturn("445800");

        // Exercice nul par defaut (pas de rattachement)
        when(exerciceRepository.findContainingDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // Sauvegarde : assigne un id 999 a la persistance
        when(repository.save(any(DeclarationTva.class))).thenAnswer(inv -> {
            DeclarationTva d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(999L);
            }
            return d;
        });

        // Ecriture creee : DTO synthetique id 7777
        when(ecritureComptableService.creer(any(EcritureComptableCreateDto.class))).thenAnswer(inv -> {
            EcritureComptableCreateDto dto = inv.getArgument(0);
            return new EcritureComptableDto(
                    7777L,
                    "JRN-OD-2026-MR-000010",
                    dto.dateComptable(),
                    dto.datePiece(),
                    1L, dto.journalCode(), "OD",
                    1L, "2026",
                    dto.libelle(), dto.reference(),
                    StatutEcriture.VALIDEE,
                    null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(),
                    null, null);
        });

        service = new DeclarationTvaServiceImpl(
                repository, ligneEcritureRepository, exerciceRepository,
                compteMappingService, ecritureComptableService);
    }

    @Test
    @DisplayName("T1 - calculer agrege collectee (CREDIT 445700) et deductible (DEBIT 445600)")
    void shouldAggregateCollecteeAndDeductible() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        when(repository.findByDateDebutAndDateFin(debut, fin)).thenReturn(Optional.empty());
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445700"), eq(debut), eq(fin), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("160.00"));
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445700"), eq(debut), eq(fin), eq(SensLigne.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445600"), eq(debut), eq(fin), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("40.00"));
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445600"), eq(debut), eq(fin), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        DeclarationTvaDto dto = service.calculer(debut, fin);
        assertEquals(0, dto.totalTvaCollectee().compareTo(new BigDecimal("160.00")));
        assertEquals(0, dto.totalTvaDeductible().compareTo(new BigDecimal("40.00")));
        assertEquals(0, dto.totalTvaADecaisser().compareTo(new BigDecimal("120.00")));
        assertEquals(StatutDeclarationTva.BROUILLON, dto.statut());
    }

    @Test
    @DisplayName("T2 - calculer idempotent : retourne l'existante sans recalcul")
    void shouldBeIdempotent() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        DeclarationTva existing = new DeclarationTva();
        existing.setId(42L);
        existing.setDateDebut(debut);
        existing.setDateFin(fin);
        existing.setTotalTvaCollectee(new BigDecimal("100.00"));
        existing.setTotalTvaDeductible(new BigDecimal("20.00"));
        existing.setTotalTvaADecaisser(new BigDecimal("80.00"));
        existing.setStatut(StatutDeclarationTva.BROUILLON);
        when(repository.findByDateDebutAndDateFin(debut, fin)).thenReturn(Optional.of(existing));

        DeclarationTvaDto dto = service.calculer(debut, fin);
        assertEquals(42L, dto.id());
        // Pas de save (idempotent)
        verify(repository, never()).save(any(DeclarationTva.class));
    }

    @Test
    @DisplayName("T3 - calculer refuse periode invalide (fin < debut)")
    void shouldRejectInvalidPeriod() {
        LocalDate debut = LocalDate.of(2026, 5, 31);
        LocalDate fin = LocalDate.of(2026, 5, 1);
        assertThrows(BusinessException.class, () -> service.calculer(debut, fin));
    }

    @Test
    @DisplayName("T4 - valider genere l'ecriture de liquidation et passe a VALIDEE")
    void shouldGenerateLiquidationEcriture() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        DeclarationTva decl = new DeclarationTva();
        decl.setId(50L);
        decl.setDateDebut(debut);
        decl.setDateFin(fin);
        decl.setTotalTvaCollectee(new BigDecimal("160.00"));
        decl.setTotalTvaDeductible(new BigDecimal("40.00"));
        decl.setTotalTvaADecaisser(new BigDecimal("120.00"));
        decl.setStatut(StatutDeclarationTva.BROUILLON);
        when(repository.findById(50L)).thenReturn(Optional.of(decl));

        DeclarationTvaDto dto = service.valider(50L);
        assertEquals(StatutDeclarationTva.VALIDEE, dto.statut());
        assertEquals(7777L, dto.ecritureLiquidationId());

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureComptableService).creer(cap.capture());
        EcritureComptableCreateDto ecriture = cap.getValue();
        assertEquals("OD", ecriture.journalCode());
        // 3 lignes : 445700 D 160, 445600 C 40, 445800 C 120
        assertEquals(3, ecriture.lignes().size());
        LigneEcritureCreateDto l1 = ecriture.lignes().get(0);
        assertEquals("445700", l1.compteCode());
        assertEquals(SensLigne.DEBIT, l1.sens());
        assertEquals(0, l1.montant().compareTo(new BigDecimal("160.00")));
        LigneEcritureCreateDto l2 = ecriture.lignes().get(1);
        assertEquals("445600", l2.compteCode());
        assertEquals(SensLigne.CREDIT, l2.sens());
        assertEquals(0, l2.montant().compareTo(new BigDecimal("40.00")));
        LigneEcritureCreateDto l3 = ecriture.lignes().get(2);
        assertEquals("445800", l3.compteCode());
        assertEquals(SensLigne.CREDIT, l3.sens());
        assertEquals(0, l3.montant().compareTo(new BigDecimal("120.00")));
    }

    @Test
    @DisplayName("T5 - valider credit reportable : DEBIT 445800 (solde negatif)")
    void shouldHandleCreditReportable() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        DeclarationTva decl = new DeclarationTva();
        decl.setId(51L);
        decl.setDateDebut(debut);
        decl.setDateFin(fin);
        decl.setTotalTvaCollectee(new BigDecimal("50.00"));
        decl.setTotalTvaDeductible(new BigDecimal("80.00"));
        decl.setTotalTvaADecaisser(new BigDecimal("-30.00"));
        decl.setStatut(StatutDeclarationTva.BROUILLON);
        when(repository.findById(51L)).thenReturn(Optional.of(decl));

        service.valider(51L);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureComptableService).creer(cap.capture());
        EcritureComptableCreateDto ecriture = cap.getValue();
        // 3 lignes : 445700 D 50, 445600 C 80, 445800 D 30 (reportable)
        assertEquals(3, ecriture.lignes().size());
        LigneEcritureCreateDto l3 = ecriture.lignes().get(2);
        assertEquals("445800", l3.compteCode());
        assertEquals(SensLigne.DEBIT, l3.sens());
        assertEquals(0, l3.montant().compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("T6 - valider equilibre exact (collectee == deductible) : pas de ligne 445800")
    void shouldOmit445800WhenBalanced() {
        DeclarationTva decl = new DeclarationTva();
        decl.setId(52L);
        decl.setDateDebut(LocalDate.of(2026, 5, 1));
        decl.setDateFin(LocalDate.of(2026, 5, 31));
        decl.setTotalTvaCollectee(new BigDecimal("100.00"));
        decl.setTotalTvaDeductible(new BigDecimal("100.00"));
        decl.setTotalTvaADecaisser(BigDecimal.ZERO);
        decl.setStatut(StatutDeclarationTva.BROUILLON);
        when(repository.findById(52L)).thenReturn(Optional.of(decl));

        service.valider(52L);

        ArgumentCaptor<EcritureComptableCreateDto> cap = ArgumentCaptor
                .forClass(EcritureComptableCreateDto.class);
        verify(ecritureComptableService).creer(cap.capture());
        // 2 lignes seulement : 445700 D / 445600 C
        assertEquals(2, cap.getValue().lignes().size());
    }

    @Test
    @DisplayName("T7 - valider refuse declaration deja validee")
    void shouldRejectAlreadyValidated() {
        DeclarationTva decl = new DeclarationTva();
        decl.setId(60L);
        decl.setStatut(StatutDeclarationTva.VALIDEE);
        when(repository.findById(60L)).thenReturn(Optional.of(decl));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.valider(60L));
        assertEquals("error.declaration.dejaValidee", ex.getMessage());
    }

    @Test
    @DisplayName("T8 - valider refuse si aucun montant (collectee + deductible == 0)")
    void shouldRejectWhenNoAmount() {
        DeclarationTva decl = new DeclarationTva();
        decl.setId(70L);
        decl.setStatut(StatutDeclarationTva.BROUILLON);
        decl.setTotalTvaCollectee(BigDecimal.ZERO);
        decl.setTotalTvaDeductible(BigDecimal.ZERO);
        decl.setTotalTvaADecaisser(BigDecimal.ZERO);
        when(repository.findById(70L)).thenReturn(Optional.of(decl));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.valider(70L));
        assertEquals("error.declaration.aucunMontant", ex.getMessage());
    }

    @Test
    @DisplayName("T9 - valider declaration inconnue -> ResourceNotFoundException")
    void shouldThrowOnUnknownId() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.valider(999L));
    }

    @Test
    @DisplayName("T10 - calculer signe contre-passations (sens inverse pris en compte)")
    void shouldAccountForContrepassationsViaSensInverse() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        when(repository.findByDateDebutAndDateFin(debut, fin)).thenReturn(Optional.empty());
        // Collectee : 200 CREDIT - 50 DEBIT (contre-passations) = 150 net
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445700"), eq(debut), eq(fin), eq(SensLigne.CREDIT)))
                .thenReturn(new BigDecimal("200.00"));
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445700"), eq(debut), eq(fin), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("50.00"));
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445600"), eq(debut), eq(fin), eq(SensLigne.DEBIT)))
                .thenReturn(new BigDecimal("80.00"));
        when(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                eq("445600"), eq(debut), eq(fin), eq(SensLigne.CREDIT)))
                .thenReturn(BigDecimal.ZERO);

        DeclarationTvaDto dto = service.calculer(debut, fin);
        assertEquals(0, dto.totalTvaCollectee().compareTo(new BigDecimal("150.00")));
        assertEquals(0, dto.totalTvaDeductible().compareTo(new BigDecimal("80.00")));
        assertEquals(0, dto.totalTvaADecaisser().compareTo(new BigDecimal("70.00")));
    }

    @Test
    @DisplayName("T11 - findById inconnu -> ResourceNotFoundException")
    void findByIdNotFound() {
        when(repository.findById(123L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(123L));
    }

    @Test
    @DisplayName("T12 - findByPeriode null safe")
    void findByPeriodeNullSafe() {
        assertEquals(Optional.empty(), service.findByPeriode(null, null));
        assertEquals(Optional.empty(), service.findByPeriode(LocalDate.now(), null));
    }

    @Test
    @DisplayName("T13 - findByPeriode trouve la declaration existante")
    void findByPeriodeExists() {
        LocalDate debut = LocalDate.of(2026, 5, 1);
        LocalDate fin = LocalDate.of(2026, 5, 31);
        DeclarationTva existing = new DeclarationTva();
        existing.setId(80L);
        existing.setDateDebut(debut);
        existing.setDateFin(fin);
        existing.setTotalTvaCollectee(BigDecimal.ZERO);
        existing.setTotalTvaDeductible(BigDecimal.ZERO);
        existing.setTotalTvaADecaisser(BigDecimal.ZERO);
        existing.setStatut(StatutDeclarationTva.BROUILLON);
        when(repository.findByDateDebutAndDateFin(debut, fin)).thenReturn(Optional.of(existing));
        Optional<DeclarationTvaDto> dto = service.findByPeriode(debut, fin);
        assertNotNull(dto.orElse(null));
        assertEquals(80L, dto.get().id());
    }
}
