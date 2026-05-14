package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.JournalComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableUpdateDto;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.JournalComptableMapper;
import com.cityprojects.citybackend.mapper.finance.JournalComptableMapperImpl;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests Surefire (Mockito leger) du {@link JournalComptableService}.
 *
 * <h3>Couverture</h3>
 * <ol>
 *   <li>T1 - {@code getOrCreate} : renvoie le journal existant si le code est deja present.</li>
 *   <li>T2 - {@code getOrCreate} : cree un nouveau journal si le code n'existe pas.</li>
 *   <li>T3 - {@code create} : refus si code deja present (BusinessException codeAlreadyExists).</li>
 *   <li>T4 - {@code create} : happy path - cree un journal avec actif=true.</li>
 *   <li>T5 - {@code update} : id inconnu -&gt; ResourceNotFoundException.</li>
 *   <li>T6 - {@code update} : mise a jour libelle + type, le code reste inchange.</li>
 *   <li>T7 - {@code desactiver} : OK transitionne actif=false ; idempotent si deja inactif.</li>
 *   <li>T8 - {@code reactiver} : OK transitionne actif=true.</li>
 *   <li>T9 - {@code findByCode} : code inconnu -&gt; ResourceNotFoundException.</li>
 *   <li>T10 - {@code findActifs} : retourne les actifs dans l'ordre des codes.</li>
 * </ol>
 */
class JournalComptableServiceTests {

    private JournalComptableRepository repository;
    private JournalComptableMapper mapper;
    private JournalComptableServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(JournalComptableRepository.class);
        mapper = new JournalComptableMapperImpl();
        service = new JournalComptableServiceImpl(repository, mapper);
    }

    private static JournalComptable journal(Long id, String code, String libelle, TypeJournal type, boolean actif) {
        JournalComptable j = new JournalComptable();
        j.setId(id);
        j.setCode(code);
        j.setLibelle(libelle);
        j.setType(type);
        j.setActif(actif);
        return j;
    }

    @Test
    @DisplayName("T1 - getOrCreate renvoie le journal existant si le code est present")
    void getOrCreateExisting() {
        JournalComptable existing = journal(7L, "VTE", "Ventes", TypeJournal.VENTE, true);
        when(repository.findByCode("VTE")).thenReturn(Optional.of(existing));

        JournalComptableDto dto = service.getOrCreate("VTE", "Ventes", TypeJournal.VENTE);
        assertEquals(7L, dto.id());
        assertEquals("VTE", dto.code());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("T2 - getOrCreate cree un nouveau journal si le code est absent")
    void getOrCreateNew() {
        when(repository.findByCode("ACH")).thenReturn(Optional.empty());
        when(repository.save(any(JournalComptable.class))).thenAnswer(inv -> {
            JournalComptable j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });

        JournalComptableDto dto = service.getOrCreate("ACH", "Achats", TypeJournal.ACHAT);
        assertEquals(42L, dto.id());
        assertEquals("ACH", dto.code());
        assertEquals(TypeJournal.ACHAT, dto.type());
        assertTrue(dto.actif());
    }

    @Test
    @DisplayName("T3 - create : refus si code deja present")
    void createCodeAlreadyExists() {
        when(repository.existsByCode("VTE")).thenReturn(true);
        JournalComptableCreateDto dto = new JournalComptableCreateDto("VTE", "Ventes", TypeJournal.VENTE);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals("error.journal.codeAlreadyExists", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("T4 - create happy path : journal cree avec actif=true")
    void createHappyPath() {
        when(repository.existsByCode("BAN")).thenReturn(false);
        when(repository.save(any(JournalComptable.class))).thenAnswer(inv -> {
            JournalComptable j = inv.getArgument(0);
            j.setId(100L);
            return j;
        });

        JournalComptableCreateDto dto = new JournalComptableCreateDto("BAN", "Banque", TypeJournal.TRESORERIE);
        JournalComptableDto result = service.create(dto);

        assertEquals(100L, result.id());
        assertEquals("BAN", result.code());
        assertEquals(TypeJournal.TRESORERIE, result.type());
        assertTrue(result.actif());

        ArgumentCaptor<JournalComptable> captor = ArgumentCaptor.forClass(JournalComptable.class);
        verify(repository).save(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getActif());
    }

    @Test
    @DisplayName("T5 - update : id inconnu -> ResourceNotFoundException")
    void updateNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        JournalComptableUpdateDto dto = new JournalComptableUpdateDto("Nouveau", TypeJournal.OPERATION_DIVERSE);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.update(999L, dto));
        assertEquals("error.journal.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T6 - update : modifie libelle + type, ne touche pas au code")
    void updateHappyPath() {
        JournalComptable j = journal(5L, "OD", "Operations Diverses", TypeJournal.OPERATION_DIVERSE, true);
        when(repository.findById(5L)).thenReturn(Optional.of(j));
        when(repository.save(any(JournalComptable.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalComptableUpdateDto dto = new JournalComptableUpdateDto("OD - operations diverses HQ", TypeJournal.OPERATION_DIVERSE);
        JournalComptableDto result = service.update(5L, dto);
        assertEquals("OD", result.code(), "Le code ne doit JAMAIS etre modifie via update");
        assertEquals("OD - operations diverses HQ", result.libelle());
    }

    @Test
    @DisplayName("T7 - desactiver passe actif=false ; idempotent si deja inactif")
    void desactiver() {
        JournalComptable j = journal(2L, "AVO", "Avoirs", TypeJournal.AVOIR, true);
        when(repository.findById(2L)).thenReturn(Optional.of(j));
        when(repository.save(any(JournalComptable.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalComptableDto result = service.desactiver(2L);
        assertFalse(result.actif());

        // Idempotent : si deja inactif, pas d'erreur
        JournalComptable inactif = journal(3L, "X", "X", TypeJournal.AVOIR, false);
        when(repository.findById(3L)).thenReturn(Optional.of(inactif));
        JournalComptableDto sameInactive = service.desactiver(3L);
        assertFalse(sameInactive.actif());
    }

    @Test
    @DisplayName("T8 - reactiver passe actif=true")
    void reactiver() {
        JournalComptable j = journal(4L, "CAI", "Caisse", TypeJournal.TRESORERIE, false);
        when(repository.findById(4L)).thenReturn(Optional.of(j));
        when(repository.save(any(JournalComptable.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalComptableDto result = service.reactiver(4L);
        assertTrue(result.actif());
    }

    @Test
    @DisplayName("T9 - findByCode : code inconnu -> ResourceNotFoundException")
    void findByCodeNotFound() {
        when(repository.findByCode("ZZZ")).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.findByCode("ZZZ"));
        assertEquals("error.journal.notFound", ex.getMessage());
    }

    @Test
    @DisplayName("T10 - findActifs : retourne les actifs (delegue au repository)")
    void findActifs() {
        when(repository.findByActifTrueOrderByCodeAsc()).thenReturn(List.of(
                journal(1L, "ACH", "Achats", TypeJournal.ACHAT, true),
                journal(2L, "BAN", "Banque", TypeJournal.TRESORERIE, true),
                journal(3L, "VTE", "Ventes", TypeJournal.VENTE, true)
        ));
        List<JournalComptableDto> result = service.findActifs();
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("ACH", result.get(0).code());
        assertEquals("BAN", result.get(1).code());
        assertEquals("VTE", result.get(2).code());
    }
}
