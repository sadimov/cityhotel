package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.entity.finance.StatutExercice;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.EcritureComptableMapper;
import com.cityprojects.citybackend.mapper.finance.EcritureComptableMapperImpl;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito leger) du {@link EcritureComptableService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - creer() OK avec D=C - statut VALIDEE + numero genere.</li>
 *   <li>T2 - creer() refuse si moins de 2 lignes.</li>
 *   <li>T3 - creer() refuse si D != C (au-dela de la tolerance 0.01).</li>
 *   <li>T4 - creer() refuse si compte inexistant ou non utilisable.</li>
 *   <li>T5 - creer() refuse si exercice CLOTURE (delegue a assertOuvert).</li>
 *   <li>T6 - creer() refuse si journal absent ({@code error.ecriture.journalInvalide}).</li>
 *   <li>T7 - creer() refuse si journal inactif.</li>
 *   <li>T8 - contrePasser() happy path : source CONTRE_PASSEE + nouvelle inversee.</li>
 *   <li>T9 - contrePasser() refuse si statut != VALIDEE.</li>
 *   <li>T10 - contrePasser() refuse si statut == CONTRE_PASSEE.</li>
 *   <li>T11 - contrePasser() refuse si motif vide.</li>
 *   <li>T12 - findById() not found -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
class EcritureComptableServiceTests {

    private EcritureComptableRepository ecritureRepository;
    private JournalComptableRepository journalRepository;
    private ExerciceRepository exerciceRepository;
    private PlanComptableGeneralRepository pcgRepository;
    private EcritureComptableMapper mapper;
    private NumerotationService numerotationService;
    private ExerciceService exerciceService;
    private EcritureComptableServiceImpl service;

    @BeforeEach
    void setUp() {
        ecritureRepository = mock(EcritureComptableRepository.class);
        journalRepository = mock(JournalComptableRepository.class);
        exerciceRepository = mock(ExerciceRepository.class);
        pcgRepository = mock(PlanComptableGeneralRepository.class);
        mapper = new EcritureComptableMapperImpl();
        numerotationService = mock(NumerotationService.class);
        exerciceService = mock(ExerciceService.class);
        service = new EcritureComptableServiceImpl(
                ecritureRepository, journalRepository, exerciceRepository,
                pcgRepository, mapper, numerotationService, exerciceService);
    }

    private static JournalComptable journal(String code, TypeJournal type, boolean actif) {
        JournalComptable j = new JournalComptable();
        j.setId(10L);
        j.setCode(code);
        j.setLibelle(code + " - libelle");
        j.setType(type);
        j.setActif(actif);
        return j;
    }

    private static Exercice exerciceOuvert() {
        Exercice e = new Exercice();
        e.setId(20L);
        e.setCode("2026");
        e.setDateDebut(LocalDate.of(2026, 1, 1));
        e.setDateFin(LocalDate.of(2026, 12, 31));
        e.setStatut(StatutExercice.OUVERT);
        return e;
    }

    private static LigneEcritureCreateDto ligne(String compte, SensLigne sens, String montant) {
        return new LigneEcritureCreateDto(null, compte, null, sens, new BigDecimal(montant), null);
    }

    private static EcritureComptableCreateDto okDto() {
        return new EcritureComptableCreateDto(
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 15),
                "VTE",
                "Vente facture FACT-2026-MR-000001",
                "FACT-2026-MR-000001",
                List.of(
                        ligne("411100", SensLigne.DEBIT, "100.00"),
                        ligne("706100", SensLigne.CREDIT, "100.00")
                ));
    }

    @Test
    @DisplayName("T1 - creer() OK avec D=C : statut VALIDEE + numero genere via JRN+codeJournal")
    void creerHappyPath() {
        when(pcgRepository.existsUtilisableByCode(anyString())).thenReturn(true);
        doNothing().when(exerciceService).assertOuvert(any(LocalDate.class));
        when(exerciceRepository.findContainingDate(any(LocalDate.class)))
                .thenReturn(Optional.of(exerciceOuvert()));
        when(journalRepository.findByCode("VTE"))
                .thenReturn(Optional.of(journal("VTE", TypeJournal.VENTE, true)));
        when(numerotationService.next(TypeNumerotation.JRN, "VTE"))
                .thenReturn("JRN-VTE-2026-MR-000001");
        AtomicLong idGen = new AtomicLong(1L);
        when(ecritureRepository.save(any(EcritureComptable.class))).thenAnswer(inv -> {
            EcritureComptable e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idGen.getAndIncrement());
            }
            return e;
        });

        EcritureComptableDto dto = service.creer(okDto());

        assertNotNull(dto);
        assertEquals("JRN-VTE-2026-MR-000001", dto.numero());
        assertEquals(StatutEcriture.VALIDEE, dto.statut());
        assertEquals(0, dto.totalDebit().compareTo(new BigDecimal("100.00")));
        assertEquals(0, dto.totalCredit().compareTo(new BigDecimal("100.00")));
        assertEquals(2, dto.lignes().size());
    }

    @Test
    @DisplayName("T2 - creer() refuse si moins de 2 lignes -> error.ecriture.minLines")
    void creerMoinsDeDeuxLignes() {
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                LocalDate.of(2026, 3, 15), null, "VTE", "lib", "ref",
                List.of(ligne("411100", SensLigne.DEBIT, "100.00")));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(dto));
        assertEquals("error.ecriture.minLines", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T3 - creer() refuse si D != C (au-dela de la tolerance 0.01) -> error.ecriture.unbalanced")
    void creerDesequilibre() {
        when(pcgRepository.existsUtilisableByCode(anyString())).thenReturn(true);
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                LocalDate.of(2026, 3, 15), null, "VTE", "lib", "ref",
                List.of(
                        ligne("411100", SensLigne.DEBIT, "100.00"),
                        ligne("706100", SensLigne.CREDIT, "90.00")
                ));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(dto));
        assertEquals("error.ecriture.unbalanced", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T4 - creer() refuse si compte inexistant ou non utilisable -> error.ecriture.compteInvalide")
    void creerCompteInvalide() {
        when(pcgRepository.existsUtilisableByCode("411100")).thenReturn(true);
        when(pcgRepository.existsUtilisableByCode("XYZ")).thenReturn(false);
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                LocalDate.of(2026, 3, 15), null, "VTE", "lib", "ref",
                List.of(
                        ligne("411100", SensLigne.DEBIT, "100.00"),
                        ligne("XYZ", SensLigne.CREDIT, "100.00")
                ));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(dto));
        assertEquals("error.ecriture.compteInvalide", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T5 - creer() refuse si exercice CLOTURE (delegue a assertOuvert)")
    void creerExerciceCloture() {
        when(pcgRepository.existsUtilisableByCode(anyString())).thenReturn(true);
        // assertOuvert leve BusinessException quand l'exercice est CLOTURE
        org.mockito.Mockito.doThrow(new BusinessException("error.exercice.cloture"))
                .when(exerciceService).assertOuvert(any(LocalDate.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(okDto()));
        assertEquals("error.exercice.cloture", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T6 - creer() refuse si journal absent -> error.ecriture.journalInvalide")
    void creerJournalAbsent() {
        when(pcgRepository.existsUtilisableByCode(anyString())).thenReturn(true);
        doNothing().when(exerciceService).assertOuvert(any(LocalDate.class));
        when(exerciceRepository.findContainingDate(any(LocalDate.class)))
                .thenReturn(Optional.of(exerciceOuvert()));
        when(journalRepository.findByCode("VTE")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(okDto()));
        assertEquals("error.ecriture.journalInvalide", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T7 - creer() refuse si journal inactif -> error.ecriture.journalInactif")
    void creerJournalInactif() {
        when(pcgRepository.existsUtilisableByCode(anyString())).thenReturn(true);
        doNothing().when(exerciceService).assertOuvert(any(LocalDate.class));
        when(exerciceRepository.findContainingDate(any(LocalDate.class)))
                .thenReturn(Optional.of(exerciceOuvert()));
        when(journalRepository.findByCode("VTE"))
                .thenReturn(Optional.of(journal("VTE", TypeJournal.VENTE, false)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.creer(okDto()));
        assertEquals("error.ecriture.journalInactif", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T8 - contrePasser() happy path : source CONTRE_PASSEE + nouvelle inversee")
    void contrePasserHappyPath() {
        // Construit une ecriture source validee : 411100 D=100, 706100 C=100
        EcritureComptable source = new EcritureComptable();
        source.setId(100L);
        source.setNumero("JRN-VTE-2026-MR-000001");
        source.setDateComptable(LocalDate.of(2026, 3, 15));
        source.setDatePiece(LocalDate.of(2026, 3, 15));
        source.setJournal(journal("VTE", TypeJournal.VENTE, true));
        source.setExercice(exerciceOuvert());
        source.setLibelle("Vente facture FACT-2026-MR-000001");
        source.setReference("FACT-2026-MR-000001");
        source.setStatut(StatutEcriture.VALIDEE);
        LigneEcriture l1 = new LigneEcriture();
        l1.setOrdre(1);
        l1.setCompteCode("411100");
        l1.setSens(SensLigne.DEBIT);
        l1.setMontant(new BigDecimal("100.00"));
        source.addLigne(l1);
        LigneEcriture l2 = new LigneEcriture();
        l2.setOrdre(2);
        l2.setCompteCode("706100");
        l2.setSens(SensLigne.CREDIT);
        l2.setMontant(new BigDecimal("100.00"));
        source.addLigne(l2);

        when(ecritureRepository.findById(100L)).thenReturn(Optional.of(source));
        doNothing().when(exerciceService).assertOuvert(any(LocalDate.class));
        when(exerciceRepository.findContainingDate(any(LocalDate.class)))
                .thenReturn(Optional.of(exerciceOuvert()));
        when(numerotationService.next(TypeNumerotation.JRN, "VTE"))
                .thenReturn("JRN-VTE-2026-MR-000002");
        AtomicLong idGen = new AtomicLong(200L);
        when(ecritureRepository.save(any(EcritureComptable.class))).thenAnswer(inv -> {
            EcritureComptable e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idGen.getAndIncrement());
            }
            return e;
        });

        EcritureComptableDto result = service.contrePasser(100L, "Erreur saisie");

        assertNotNull(result);
        assertEquals("JRN-VTE-2026-MR-000002", result.numero());
        assertEquals(StatutEcriture.VALIDEE, result.statut());
        assertEquals(100L, result.ecritureSourceId());
        assertEquals("CP-JRN-VTE-2026-MR-000001", result.reference());
        assertTrue(result.libelle().contains("Contre-passation"));
        assertTrue(result.libelle().contains("Erreur saisie"));
        // Verifier l'inversion : 411100 etait DEBIT, doit etre CREDIT ; 706100 etait CREDIT, doit etre DEBIT.
        assertEquals(2, result.lignes().size());
        assertEquals("411100", result.lignes().get(0).compteCode());
        assertEquals(SensLigne.CREDIT, result.lignes().get(0).sens());
        assertEquals("706100", result.lignes().get(1).compteCode());
        assertEquals(SensLigne.DEBIT, result.lignes().get(1).sens());

        // Verifier que la source a ete sauvee en CONTRE_PASSEE avec contrePasseeParId
        ArgumentCaptor<EcritureComptable> captor = ArgumentCaptor.forClass(EcritureComptable.class);
        verify(ecritureRepository, atLeastOnce()).save(captor.capture());
        boolean sourceUpdatedFound = captor.getAllValues().stream()
                .anyMatch(e -> e.getId() != null && e.getId() == 100L
                        && e.getStatut() == StatutEcriture.CONTRE_PASSEE
                        && e.getContrePasseeParId() != null);
        assertTrue(sourceUpdatedFound, "L'ecriture source doit avoir ete sauvee en CONTRE_PASSEE");
    }

    @Test
    @DisplayName("T9 - contrePasser() refuse si statut != VALIDEE -> error.ecriture.notValidated")
    void contrePasserNotValidated() {
        EcritureComptable brouillon = new EcritureComptable();
        brouillon.setId(101L);
        brouillon.setNumero("JRN-VTE-2026-MR-000003");
        brouillon.setStatut(StatutEcriture.BROUILLON);
        brouillon.setJournal(journal("VTE", TypeJournal.VENTE, true));
        brouillon.setExercice(exerciceOuvert());
        brouillon.setLibelle("libelle");
        when(ecritureRepository.findById(101L)).thenReturn(Optional.of(brouillon));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.contrePasser(101L, "motif"));
        assertEquals("error.ecriture.notValidated", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T10 - contrePasser() refuse si deja CONTRE_PASSEE -> error.ecriture.alreadyContrePassed")
    void contrePasserDejaContrePasse() {
        EcritureComptable cp = new EcritureComptable();
        cp.setId(102L);
        cp.setStatut(StatutEcriture.CONTRE_PASSEE);
        cp.setJournal(journal("VTE", TypeJournal.VENTE, true));
        cp.setExercice(exerciceOuvert());
        cp.setLibelle("libelle");
        when(ecritureRepository.findById(102L)).thenReturn(Optional.of(cp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.contrePasser(102L, "motif"));
        assertEquals("error.ecriture.alreadyContrePassed", ex.getMessage());
        verify(ecritureRepository, never()).save(any());
    }

    @Test
    @DisplayName("T11 - contrePasser() refuse si motif vide -> error.motif.required")
    void contrePasserMotifVide() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.contrePasser(100L, ""));
        assertEquals("error.motif.required", ex.getMessage());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> service.contrePasser(100L, "   "));
        assertEquals("error.motif.required", ex2.getMessage());

        BusinessException ex3 = assertThrows(BusinessException.class,
                () -> service.contrePasser(100L, null));
        assertEquals("error.motif.required", ex3.getMessage());
    }

    @Test
    @DisplayName("T12 - findById() not found -> ResourceNotFoundException")
    void findByIdNotFound() {
        when(ecritureRepository.findById(999L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.findById(999L));
        assertEquals("error.ecriture.notFound", ex.getMessage());
    }
}
