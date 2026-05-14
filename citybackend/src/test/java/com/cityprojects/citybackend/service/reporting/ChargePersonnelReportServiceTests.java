package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargePersonnelReportServiceTests {

    @Mock
    private TacheRepository tacheRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private ChargePersonnelReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChargePersonnelReportServiceImpl(tacheRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - personnel 5 : 2 taches dont 1 terminee + 30min")
    void shouldAggregateCharge() {
        Tache t1 = new Tache();
        t1.setPersonnelId(5L);
        t1.setStatut(StatutTache.TERMINEE);
        Instant start = Instant.now().minusSeconds(3600);
        t1.setHeureDebutReelle(start);
        t1.setHeureFinReelle(start.plusSeconds(1800)); // 30 min
        Tache t2 = new Tache();
        t2.setPersonnelId(5L);
        t2.setStatut(StatutTache.PLANIFIEE);
        when(tacheRepository.findOnRange(any(), any())).thenReturn(List.of(t1, t2));

        ChargePersonnelDto dto = service.computeCharge(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertEquals(1, dto.personnels().size());
        assertEquals(2L, dto.personnels().get(0).nbAssignees());
        assertEquals(1L, dto.personnels().get(0).nbTerminees());
        assertEquals(30L, dto.personnels().get(0).dureeTotaleMin());
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeCharge(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - aucune tache")
    void shouldHandleEmpty() {
        when(tacheRepository.findOnRange(any(), any())).thenReturn(List.of());

        ChargePersonnelDto dto = service.computeCharge(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertTrue(dto.personnels().isEmpty());
    }
}
