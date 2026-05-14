package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.JournalEditionDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalFilterDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
 * Tests Surefire du {@link JournalEditionService} (B5).
 */
class JournalEditionServiceTests {

    private EcritureComptableRepository ecritureRepo;
    private JournalComptableRepository journalRepo;
    private PlanComptableGeneralRepository pcgRepo;
    private XlsxExportService xlsxService;
    private JournalEditionServiceImpl service;

    @BeforeEach
    void setUp() {
        ecritureRepo = mock(EcritureComptableRepository.class);
        journalRepo = mock(JournalComptableRepository.class);
        pcgRepo = mock(PlanComptableGeneralRepository.class);
        xlsxService = mock(XlsxExportService.class);
        service = new JournalEditionServiceImpl(ecritureRepo, journalRepo, pcgRepo, xlsxService);
    }

    private static JournalComptable journal() {
        JournalComptable j = new JournalComptable();
        j.setId(10L);
        j.setCode("VTE");
        j.setLibelle("Ventes");
        j.setType(TypeJournal.VENTE);
        j.setActif(Boolean.TRUE);
        return j;
    }

    private static LigneEcriture ligne(String compte, SensLigne sens, String montant, int ordre) {
        LigneEcriture l = new LigneEcriture();
        l.setId((long) ordre);
        l.setCompteCode(compte);
        l.setSens(sens);
        l.setMontant(new BigDecimal(montant));
        l.setOrdre(ordre);
        return l;
    }

    private static EcritureComptable ecritureFromList(Long id, LocalDate date, String numero,
                                                       List<LigneEcriture> lignes) {
        EcritureComptable ec = new EcritureComptable();
        ec.setId(id);
        ec.setDateComptable(date);
        ec.setNumero(numero);
        ec.setLibelle("vente " + numero);
        ec.setReference("FACT-" + numero);
        for (LigneEcriture l : lignes) {
            ec.addLigne(l);
        }
        return ec;
    }

    @Test
    @DisplayName("T1 - compute() : 2 ecritures sur periode, totaux corrects")
    void computeHappyPath() {
        when(journalRepo.findById(10L)).thenReturn(Optional.of(journal()));
        EcritureComptable ec1 = ecritureFromList(1L, LocalDate.of(2026, 3, 5), "N1",
                List.of(ligne("411100", SensLigne.DEBIT, "100.00", 1),
                        ligne("706100", SensLigne.CREDIT, "100.00", 2)));
        EcritureComptable ec2 = ecritureFromList(2L, LocalDate.of(2026, 3, 10), "N2",
                List.of(ligne("411100", SensLigne.DEBIT, "200.00", 1),
                        ligne("706100", SensLigne.CREDIT, "200.00", 2)));
        when(ecritureRepo.findByJournalIdAndDateBetween(eq(10L), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ec1, ec2)));
        when(pcgRepo.findByCompteCode(any())).thenReturn(Optional.empty());

        JournalEditionDto dto = service.compute(new JournalFilterDto(10L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertNotNull(dto);
        assertEquals("VTE", dto.journalCode());
        assertEquals(2, dto.ecritures().size());
        assertEquals(0, dto.totalDebit().compareTo(new BigDecimal("300.00")));
        assertEquals(0, dto.totalCredit().compareTo(new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("T2 - compute() refuse si journalId null -> error.etat.journalIdRequired")
    void journalIdRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(new JournalFilterDto(null,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));
        assertEquals("error.etat.journalIdRequired", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - compute() refuse si journal inexistant -> ResourceNotFoundException")
    void journalNotFound() {
        when(journalRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                service.compute(new JournalFilterDto(99L,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));
    }

    @Test
    @DisplayName("T4 - compute() refuse si dateFin avant dateDebut -> error.etat.dateRangeInvalide")
    void dateRangeInvalide() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.compute(new JournalFilterDto(10L,
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1))));
        assertEquals("error.etat.dateRangeInvalide", ex.getMessage());
    }

    @Test
    @DisplayName("T5 - compute() : aucune ecriture -> totaux a 0")
    void emptyJournal() {
        when(journalRepo.findById(10L)).thenReturn(Optional.of(journal()));
        when(ecritureRepo.findByJournalIdAndDateBetween(eq(10L), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        JournalEditionDto dto = service.compute(new JournalFilterDto(10L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertEquals(0, dto.totalDebit().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, dto.totalCredit().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, dto.ecritures().size());
    }
}
