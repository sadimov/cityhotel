package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.TacheGroupBy;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecapTachesReportServiceTests {

    @Mock
    private TacheRepository tacheRepository;
    @Mock
    private XlsxExportService xlsxExportService;

    private RecapTachesReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecapTachesReportServiceImpl(tacheRepository, xlsxExportService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T1 - groupage par STATUT")
    void shouldGroupByStatut() {
        Tache t1 = new Tache();
        t1.setStatut(StatutTache.TERMINEE);
        t1.setDatePlanifiee(LocalDate.of(2026, 5, 1));
        t1.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
        Tache t2 = new Tache();
        t2.setStatut(StatutTache.PLANIFIEE);
        t2.setDatePlanifiee(LocalDate.of(2026, 5, 2));
        t2.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
        when(tacheRepository.findOnRange(any(), any())).thenReturn(List.of(t1, t2));

        RecapTacheDto dto = service.computeRecap(LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1), TacheGroupBy.STATUT);

        assertEquals(2L, dto.totalTaches());
        assertEquals(2, dto.breakdown().size());
    }

    @Test
    @DisplayName("T2 - dates invalides")
    void shouldRejectInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeRecap(LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 1, 1), TacheGroupBy.JOUR));
        assertEquals("error.report.dateRange.invalid", ex.getMessage());
    }

    @Test
    @DisplayName("T3 - groupBy null")
    void shouldRejectNullGroupBy() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.computeRecap(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 1), null));
        assertEquals("error.report.groupBy.required", ex.getMessage());
    }
}
