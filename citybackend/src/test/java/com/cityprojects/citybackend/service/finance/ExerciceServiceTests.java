package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.ExerciceDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.StatutExercice;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.ExerciceMapper;
import com.cityprojects.citybackend.mapper.finance.ExerciceMapperImpl;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * Tests Surefire (Mockito leger) du {@link ExerciceService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code getOrCreateCurrent} renvoie l'exercice existant.</li>
 *   <li>T2 - {@code getOrCreateCurrent} cree un exercice annee calendaire
 *       quand aucun n'existe.</li>
 *   <li>T3 - {@code assertOuvert} OK pour une date dans un exercice OUVERT.</li>
 *   <li>T4 - {@code assertOuvert} leve BusinessException error.exercice.cloture
 *       pour un exercice EN_CLOTURE.</li>
 *   <li>T5 - {@code assertOuvert} leve BusinessException error.exercice.cloture
 *       pour un exercice CLOTURE.</li>
 *   <li>T6 - {@code cloturer} : transition OUVERT -&gt; CLOTURE avec dateCloture
 *       + clotureBy renseignes.</li>
 *   <li>T7 - {@code cloturer} : exercice deja CLOTURE leve BusinessException
 *       dejaCloture.</li>
 *   <li>T8 - {@code findById} : id inconnu -&gt; ResourceNotFoundException.</li>
 * </ol>
 */
class ExerciceServiceTests {

    private ExerciceRepository repository;
    private ExerciceMapper mapper;
    private ExerciceServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ExerciceRepository.class);
        mapper = new ExerciceMapperImpl();
        service = new ExerciceServiceImpl(repository, mapper);
    }

    private static Exercice exerciceCourant(StatutExercice statut) {
        Exercice e = new Exercice();
        e.setId(1L);
        int year = Year.now().getValue();
        e.setCode(String.valueOf(year));
        e.setDateDebut(LocalDate.of(year, 1, 1));
        e.setDateFin(LocalDate.of(year, 12, 31));
        e.setStatut(statut);
        return e;
    }

    @Test
    @DisplayName("T1 - getOrCreateCurrent renvoie l'exercice existant")
    void getOrCreateCurrentExisting() {
        Exercice existing = exerciceCourant(StatutExercice.OUVERT);
        when(repository.findContainingDateForUpdate(any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        ExerciceDto dto = service.getOrCreateCurrent();
        assertEquals(1L, dto.id());
        assertEquals(StatutExercice.OUVERT, dto.statut());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("T2 - getOrCreateCurrent cree un exercice annee calendaire si absent")
    void getOrCreateCurrentNew() {
        when(repository.findContainingDateForUpdate(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        int year = Year.now().getValue();
        when(repository.findByCode(String.valueOf(year))).thenReturn(Optional.empty());
        when(repository.save(any(Exercice.class))).thenAnswer(inv -> {
            Exercice e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        ExerciceDto dto = service.getOrCreateCurrent();
        assertEquals(42L, dto.id());
        assertEquals(String.valueOf(year), dto.code());
        assertEquals(LocalDate.of(year, 1, 1), dto.dateDebut());
        assertEquals(LocalDate.of(year, 12, 31), dto.dateFin());
        assertEquals(StatutExercice.OUVERT, dto.statut());
    }

    @Test
    @DisplayName("T3 - assertOuvert OK pour exercice OUVERT")
    void assertOuvertOk() {
        Exercice e = exerciceCourant(StatutExercice.OUVERT);
        when(repository.findContainingDate(any(LocalDate.class))).thenReturn(Optional.of(e));
        assertDoesNotThrow(() -> service.assertOuvert(LocalDate.now()));
    }

    @Test
    @DisplayName("T4 - assertOuvert refuse exercice EN_CLOTURE")
    void assertOuvertEnCloture() {
        Exercice e = exerciceCourant(StatutExercice.EN_CLOTURE);
        when(repository.findContainingDate(any(LocalDate.class))).thenReturn(Optional.of(e));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertOuvert(LocalDate.now()));
        assertEquals("error.exercice.cloture", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - assertOuvert refuse exercice CLOTURE")
    void assertOuvertCloture() {
        Exercice e = exerciceCourant(StatutExercice.CLOTURE);
        when(repository.findContainingDate(any(LocalDate.class))).thenReturn(Optional.of(e));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertOuvert(LocalDate.now()));
        assertEquals("error.exercice.cloture", ex.getMessage());
    }

    @Test
    @DisplayName("T6 - cloturer transition OUVERT -> CLOTURE + horodatage + auteur")
    void cloturerHappyPath() {
        Exercice e = exerciceCourant(StatutExercice.OUVERT);
        when(repository.findById(1L)).thenReturn(Optional.of(e));
        when(repository.save(any(Exercice.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciceDto dto = service.cloturer(1L);
        assertEquals(StatutExercice.CLOTURE, dto.statut());
        assertNotNull(dto.dateCloture());
        assertNotNull(dto.clotureBy());

        ArgumentCaptor<Exercice> captor = ArgumentCaptor.forClass(Exercice.class);
        verify(repository).save(captor.capture());
        Exercice saved = captor.getValue();
        assertEquals(StatutExercice.CLOTURE, saved.getStatut());
        assertEquals(LocalDate.now(), saved.getDateCloture());
        // clotureBy fallback "system" car SecurityContext est vide en test
        assertEquals("system", saved.getClotureBy());
    }

    @Test
    @DisplayName("T7 - cloturer un exercice deja CLOTURE leve BusinessException dejaCloture")
    void cloturerDejaCloture() {
        Exercice e = exerciceCourant(StatutExercice.CLOTURE);
        when(repository.findById(1L)).thenReturn(Optional.of(e));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cloturer(1L));
        assertEquals("error.exercice.dejaCloture", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("T8 - findById id inconnu leve ResourceNotFoundException")
    void findByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.findById(999L));
        assertEquals("error.exercice.notFound", ex.getMessage());
    }
}
