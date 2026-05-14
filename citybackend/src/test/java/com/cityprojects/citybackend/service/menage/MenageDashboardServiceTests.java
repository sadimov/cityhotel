package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.DashboardMenageDto;
import com.cityprojects.citybackend.dto.menage.KpiMenageDto;
import com.cityprojects.citybackend.dto.menage.PerformancePersonnelDto;
import com.cityprojects.citybackend.dto.menage.StatistiquesMenageDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito du {@link MenageDashboardServiceImpl} (sous-tour F1).
 *
 * <p>Couvrent les 5 endpoints exposes par le service :</p>
 * <ul>
 *   <li>{@link #getStatistiquesJour()} : agregat du jour</li>
 *   <li>{@link #getStatistiquesPeriode(LocalDate, LocalDate)} : agregat periode</li>
 *   <li>{@link #getDashboard()} : statistiques + top retards + dispos</li>
 *   <li>{@link #getKpi()} : indicateurs synthese tendance 30 jours</li>
 *   <li>{@link #getPerformancePersonnel(Long, LocalDate, LocalDate)} : stats agent</li>
 * </ul>
 *
 * <p>Pas de Spring : tous les collaborateurs (3 repositories) sont mockes,
 * le {@link Clock} est fixe a {@code 2026-05-14T12:00:00Z} pour avoir un
 * "aujourd'hui" deterministe.</p>
 */
@ExtendWith(MockitoExtension.class)
class MenageDashboardServiceTests {

    private static final long HOTEL_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 14);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("UTC")).plusHours(12).toInstant(),
            ZoneId.of("UTC"));

    @Mock private TacheRepository tacheRepository;
    @Mock private PersonnelRepository personnelRepository;
    @Mock private ChambreRepository chambreRepository;

    private MenageDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        com.cityprojects.citybackend.common.tenant.TenantContext.set(HOTEL_ID);
        service = new MenageDashboardServiceImpl(
                tacheRepository, personnelRepository, chambreRepository, FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() {
        com.cityprojects.citybackend.common.tenant.TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────
    // getStatistiquesJour
    // ─────────────────────────────────────────────────────────────────

    @Test
    void getStatistiquesJour_agregateAllCountersForToday() {
        when(personnelRepository.countByActifTrue()).thenReturn(8L);
        when(tacheRepository.countByDatePlanifiee(TODAY)).thenReturn(20L);
        when(tacheRepository.countByDatePlanifieeAndStatut(TODAY, StatutTache.EN_COURS)).thenReturn(5L);
        when(tacheRepository.countByDatePlanifieeAndStatut(TODAY, StatutTache.TERMINEE)).thenReturn(12L);
        when(tacheRepository.countByDatePlanifieeAndPriorite(TODAY, 3)).thenReturn(2L);
        when(tacheRepository.countByStatutForDate(TODAY)).thenReturn(List.<Object[]>of(
                new Object[]{StatutTache.TERMINEE, 12L},
                new Object[]{StatutTache.EN_COURS, 5L},
                new Object[]{StatutTache.PLANIFIEE, 3L}));
        when(tacheRepository.countByTypeForDate(TODAY)).thenReturn(List.<Object[]>of(
                new Object[]{TypeNettoyage.QUOTIDIEN, 15L},
                new Object[]{TypeNettoyage.GRAND_MENAGE, 5L}));
        when(tacheRepository.countByPrioriteForDate(TODAY)).thenReturn(List.<Object[]>of(
                new Object[]{1, 10L},
                new Object[]{2, 8L},
                new Object[]{3, 2L}));
        when(tacheRepository.findTermineesAvecDureeOnPeriode(TODAY, TODAY)).thenReturn(List.of(
                tacheTerminee(45),
                tacheTerminee(60),
                tacheTerminee(30)));
        // findTopEnRetard est appele pour le compte enRetard (periode incluant today)
        when(tacheRepository.findTopEnRetard(eq(TODAY), any(), any(Pageable.class)))
                .thenReturn(List.of(tachePlanifiee(TODAY, 101L)));

        StatistiquesMenageDto stats = service.getStatistiquesJour();

        assertThat(stats.dateReference()).isEqualTo(TODAY);
        assertThat(stats.nombrePersonnelActif()).isEqualTo(8L);
        assertThat(stats.nombreTachesAujourdhui()).isEqualTo(20L);
        assertThat(stats.nombreTachesEnCours()).isEqualTo(5L);
        assertThat(stats.nombreTachesTerminees()).isEqualTo(12L);
        assertThat(stats.nombreTachesUrgentes()).isEqualTo(2L);
        assertThat(stats.repartitionParStatut())
                .containsEntry("TERMINEE", 12L)
                .containsEntry("EN_COURS", 5L)
                .containsEntry("PLANIFIEE", 3L);
        assertThat(stats.repartitionParType())
                .containsEntry("QUOTIDIEN", 15L)
                .containsEntry("GRAND_MENAGE", 5L);
        assertThat(stats.repartitionParPriorite())
                .containsEntry("1", 10L)
                .containsEntry("2", 8L)
                .containsEntry("3", 2L);
        // (45 + 60 + 30) / 3 = 45
        assertThat(stats.tempsRealisationMoyen()).isEqualTo(45.0);
        // 12 / 20 * 100 = 60
        assertThat(stats.tauxRealisationPourcentage()).isEqualTo(60.0);
        // V1 : non implemente
        assertThat(stats.nombreConflitsPlanning()).isZero();
    }

    @Test
    void getStatistiquesJour_avoidsDivisionByZeroWhenNoTache() {
        when(personnelRepository.countByActifTrue()).thenReturn(5L);
        when(tacheRepository.countByDatePlanifiee(TODAY)).thenReturn(0L);
        when(tacheRepository.countByStatutForDate(TODAY)).thenReturn(List.of());
        when(tacheRepository.countByTypeForDate(TODAY)).thenReturn(List.of());
        when(tacheRepository.countByPrioriteForDate(TODAY)).thenReturn(List.of());
        when(tacheRepository.findTermineesAvecDureeOnPeriode(TODAY, TODAY)).thenReturn(List.of());

        StatistiquesMenageDto stats = service.getStatistiquesJour();

        // Taux de realisation = 0 (et pas NaN) quand total = 0.
        assertThat(stats.tauxRealisationPourcentage()).isEqualTo(0.0);
        // Temps moyen = null quand aucune tache (et pas 0 ni NaN).
        assertThat(stats.tempsRealisationMoyen()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // getStatistiquesPeriode
    // ─────────────────────────────────────────────────────────────────

    @Test
    void getStatistiquesPeriode_usesPeriodCountersWhenMultiDay() {
        LocalDate from = TODAY.minusDays(7);
        LocalDate to = TODAY;

        when(personnelRepository.countByActifTrue()).thenReturn(8L);
        when(tacheRepository.countByDatePlanifieeBetween(from, to)).thenReturn(150L);
        when(tacheRepository.countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.EN_COURS)).thenReturn(20L);
        when(tacheRepository.countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.TERMINEE)).thenReturn(110L);
        when(tacheRepository.countByPrioriteForPeriode(from, to)).thenReturn(List.<Object[]>of(
                new Object[]{1, 80L}, new Object[]{2, 50L}, new Object[]{3, 20L}));
        when(tacheRepository.countByStatutForPeriode(from, to)).thenReturn(List.<Object[]>of(
                new Object[]{StatutTache.TERMINEE, 110L}));
        when(tacheRepository.countByTypeForPeriode(from, to)).thenReturn(List.<Object[]>of(
                new Object[]{TypeNettoyage.QUOTIDIEN, 100L}));
        when(tacheRepository.findTermineesAvecDureeOnPeriode(from, to)).thenReturn(List.of());

        StatistiquesMenageDto stats = service.getStatistiquesPeriode(from, to);

        assertThat(stats.dateReference()).isEqualTo(from);
        assertThat(stats.nombreTachesAujourdhui()).isEqualTo(150L);
        assertThat(stats.nombreTachesUrgentes()).isEqualTo(20L); // priorite 3 sur la map
    }

    @Test
    void getStatistiquesPeriode_throwsWhenInvalidRange() {
        assertThatThrownBy(() -> service.getStatistiquesPeriode(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error.menage.statistiques.periodeInvalide");
    }

    // ─────────────────────────────────────────────────────────────────
    // getDashboard
    // ─────────────────────────────────────────────────────────────────

    @Test
    void getDashboard_includesStatsTopRetardsAndDispos() {
        // Stats du jour (mocks minimaux)
        lenient().when(personnelRepository.countByActifTrue()).thenReturn(8L);
        lenient().when(tacheRepository.countByDatePlanifiee(TODAY)).thenReturn(5L);
        lenient().when(tacheRepository.countByDatePlanifieeAndStatut(TODAY, StatutTache.EN_COURS)).thenReturn(2L);
        lenient().when(tacheRepository.countByDatePlanifieeAndStatut(TODAY, StatutTache.TERMINEE)).thenReturn(2L);
        lenient().when(tacheRepository.countByDatePlanifieeAndPriorite(TODAY, 3)).thenReturn(0L);
        lenient().when(tacheRepository.countByStatutForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.countByTypeForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.countByPrioriteForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.findTermineesAvecDureeOnPeriode(TODAY, TODAY)).thenReturn(List.of());

        // Top retards : 1 tache en retard sur chambre 12
        Tache retard = tachePlanifiee(TODAY.minusDays(1), 12L);
        when(tacheRepository.findTopEnRetard(eq(TODAY), any(), any(Pageable.class)))
                .thenReturn(List.of(retard));
        Chambre chambre = new Chambre();
        chambre.setChambreId(12L);
        chambre.setNumeroChambre("305");
        when(chambreRepository.findById(12L)).thenReturn(Optional.of(chambre));

        // Personnels disponibles aujourd'hui : 2 agents
        Personnel a = new Personnel();
        a.setPersonnelId(101L);
        a.setPrenom("Ahmed");
        a.setNom("Hassan");
        Personnel b = new Personnel();
        b.setPersonnelId(102L);
        b.setPrenom("Mariam");
        b.setNom("Diallo");
        when(personnelRepository.findDisponiblesAtDate(TODAY)).thenReturn(List.of(a, b));

        DashboardMenageDto dashboard = service.getDashboard();

        assertThat(dashboard.statistiques()).isNotNull();
        assertThat(dashboard.statistiques().nombrePersonnelActif()).isEqualTo(8L);
        assertThat(dashboard.tachesEnRetard()).hasSize(1);
        assertThat(dashboard.tachesEnRetard().get(0).numeroChambre()).isEqualTo("305");
        assertThat(dashboard.tachesEnRetard().get(0).libelleStatut()).isEqualTo("Planifiée");
        assertThat(dashboard.personnelsDisponibles()).hasSize(2);
        assertThat(dashboard.personnelsDisponibles().get(0).nomComplet()).isEqualTo("Ahmed Hassan");
        assertThat(dashboard.personnelsDisponibles().get(1).nomComplet()).isEqualTo("Mariam Diallo");
    }

    @Test
    void getDashboard_resilientWhenChambreNotFound() {
        // Stats minimal
        lenient().when(personnelRepository.countByActifTrue()).thenReturn(0L);
        lenient().when(tacheRepository.countByDatePlanifiee(TODAY)).thenReturn(0L);
        lenient().when(tacheRepository.countByStatutForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.countByTypeForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.countByPrioriteForDate(TODAY)).thenReturn(List.of());
        lenient().when(tacheRepository.findTermineesAvecDureeOnPeriode(TODAY, TODAY)).thenReturn(List.of());

        when(tacheRepository.findTopEnRetard(eq(TODAY), any(), any(Pageable.class)))
                .thenReturn(List.of(tachePlanifiee(TODAY.minusDays(2), 99L)));
        // Chambre supprimee entre temps
        when(chambreRepository.findById(99L)).thenReturn(Optional.empty());
        when(personnelRepository.findDisponiblesAtDate(TODAY)).thenReturn(List.of());

        DashboardMenageDto dashboard = service.getDashboard();

        assertThat(dashboard.tachesEnRetard()).hasSize(1);
        assertThat(dashboard.tachesEnRetard().get(0).numeroChambre()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // getPerformancePersonnel
    // ─────────────────────────────────────────────────────────────────

    @Test
    void getPerformancePersonnel_computesAssignedTerminatedAndRatio() {
        Long personnelId = 42L;
        LocalDate from = TODAY.minusDays(30);
        LocalDate to = TODAY;

        Personnel p = new Personnel();
        p.setPersonnelId(personnelId);
        p.setPrenom("Salem");
        p.setNom("Ould");
        when(personnelRepository.findById(personnelId)).thenReturn(Optional.of(p));
        when(tacheRepository.countByPersonnelIdAndDatePlanifieeBetween(personnelId, from, to))
                .thenReturn(40L);
        when(tacheRepository.countByPersonnelIdAndDatePlanifieeBetweenAndStatut(
                personnelId, from, to, StatutTache.TERMINEE)).thenReturn(32L);
        when(tacheRepository.countByPersonnelIdAndDatePlanifieeBetweenAndStatut(
                personnelId, from, to, StatutTache.ANNULEE)).thenReturn(3L);
        when(tacheRepository.findTermineesAvecDureeForPersonnel(personnelId, from, to))
                .thenReturn(List.of(tacheTerminee(40), tacheTerminee(50)));

        PerformancePersonnelDto perf = service.getPerformancePersonnel(personnelId, from, to);

        assertThat(perf.personnelId()).isEqualTo(personnelId);
        assertThat(perf.nomComplet()).isEqualTo("Salem Ould");
        assertThat(perf.dateDebut()).isEqualTo(from);
        assertThat(perf.dateFin()).isEqualTo(to);
        assertThat(perf.nombreTachesAssignees()).isEqualTo(40L);
        assertThat(perf.nombreTachesTerminees()).isEqualTo(32L);
        assertThat(perf.nombreTachesAnnulees()).isEqualTo(3L);
        assertThat(perf.tempsRealisationMoyen()).isEqualTo(45.0);
        // 32 / 40 * 100 = 80
        assertThat(perf.tauxRealisation()).isEqualTo(80.0);
    }

    @Test
    void getPerformancePersonnel_throwsWhenPersonnelMissing() {
        when(personnelRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPerformancePersonnel(999L, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.personnel.notFound");
    }

    // ─────────────────────────────────────────────────────────────────
    // getKpi
    // ─────────────────────────────────────────────────────────────────

    @Test
    void getKpi_aggregatesTodayCountersAndTrend() {
        when(personnelRepository.countByActifTrue()).thenReturn(6L);
        when(tacheRepository.countByDatePlanifiee(TODAY)).thenReturn(10L);
        when(tacheRepository.countByDatePlanifieeAndStatut(TODAY, StatutTache.TERMINEE)).thenReturn(7L);
        when(tacheRepository.findTopEnRetard(eq(TODAY), any(), any(Pageable.class)))
                .thenReturn(List.of(tachePlanifiee(TODAY.minusDays(1), 1L),
                        tachePlanifiee(TODAY.minusDays(2), 2L)));
        when(tacheRepository.findTermineesAvecDureeOnPeriode(TODAY.minusDays(30), TODAY))
                .thenReturn(List.of(tacheTerminee(50), tacheTerminee(70)));

        KpiMenageDto kpi = service.getKpi();

        assertThat(kpi.dateReference()).isEqualTo(TODAY);
        assertThat(kpi.nombrePersonnelActif()).isEqualTo(6L);
        assertThat(kpi.nombreTachesAujourdhui()).isEqualTo(10L);
        assertThat(kpi.nombreTachesEnRetard()).isEqualTo(2L);
        // 7 / 10 * 100 = 70
        assertThat(kpi.tauxRealisationJour()).isEqualTo(70.0);
        assertThat(kpi.tempsRealisationMoyen30Jours()).isEqualTo(60.0);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private Tache tachePlanifiee(LocalDate date, Long chambreId) {
        Tache t = new Tache();
        t.setTacheId(date.toEpochDay()); // id unique stable
        t.setHotelId(HOTEL_ID);
        t.setChambreId(chambreId);
        t.setStatut(StatutTache.PLANIFIEE);
        t.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
        t.setPriorite(1);
        t.setDatePlanifiee(date);
        return t;
    }

    private Tache tacheTerminee(long dureeMinutes) {
        Tache t = new Tache();
        t.setStatut(StatutTache.TERMINEE);
        Instant debut = TODAY.atStartOfDay(ZoneId.of("UTC")).plusHours(9).toInstant();
        t.setHeureDebutReelle(debut);
        t.setHeureFinReelle(debut.plusSeconds(dureeMinutes * 60));
        return t;
    }
}
