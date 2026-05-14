package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.menage.DashboardMenageDto;
import com.cityprojects.citybackend.dto.menage.KpiMenageDto;
import com.cityprojects.citybackend.dto.menage.PerformancePersonnelDto;
import com.cityprojects.citybackend.dto.menage.PersonnelCarteDto;
import com.cityprojects.citybackend.dto.menage.StatistiquesMenageDto;
import com.cityprojects.citybackend.dto.menage.TacheCarteDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation de {@link MenageDashboardService}.
 *
 * <p>Service read-only. Toutes les methodes lisent les repositories et
 * calculent des agregats sans modifier d'etat.</p>
 *
 * <p>Multi-tenant : {@code @RequireTenant} au niveau classe.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class MenageDashboardServiceImpl implements MenageDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(MenageDashboardServiceImpl.class);

    /** Profondeur de la tendance "temps moyen 30 jours" du KPI. */
    private static final int KPI_TREND_DAYS = 30;

    /** Limite du top "taches en retard" affiche en vignettes Dashboard. */
    private static final int DASHBOARD_TOP_RETARD = 5;

    /** Priorite "Critique" (cf. Tache.priorite @Min(1)/@Max(3)). */
    private static final int PRIORITE_CRITIQUE = 3;

    private final TacheRepository tacheRepository;
    private final PersonnelRepository personnelRepository;
    private final ChambreRepository chambreRepository;
    private final Clock clock;

    public MenageDashboardServiceImpl(TacheRepository tacheRepository,
                                       PersonnelRepository personnelRepository,
                                       ChambreRepository chambreRepository,
                                       Clock clock) {
        this.tacheRepository = tacheRepository;
        this.personnelRepository = personnelRepository;
        this.chambreRepository = chambreRepository;
        this.clock = clock;
    }

    @Override
    public DashboardMenageDto getDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        StatistiquesMenageDto stats = computeStatistiques(today, today, today);

        List<TacheCarteDto> retard = tacheRepository
                .findTopEnRetard(today, now, PageRequest.of(0, DASHBOARD_TOP_RETARD))
                .stream()
                .map(this::toCarte)
                .toList();

        List<PersonnelCarteDto> dispos = personnelRepository
                .findDisponiblesAtDate(today)
                .stream()
                .map(p -> new PersonnelCarteDto(p.getPersonnelId(), p.getNomComplet()))
                .toList();

        return new DashboardMenageDto(stats, retard, dispos);
    }

    @Override
    public StatistiquesMenageDto getStatistiquesJour() {
        LocalDate today = LocalDate.now(clock);
        return computeStatistiques(today, today, today);
    }

    @Override
    public StatistiquesMenageDto getStatistiquesPeriode(LocalDate dateDebut, LocalDate dateFin) {
        LocalDate from = (dateDebut != null) ? dateDebut : LocalDate.now(clock);
        LocalDate to = (dateFin != null) ? dateFin : from;
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("error.menage.statistiques.periodeInvalide");
        }
        return computeStatistiques(from, from, to);
    }

    @Override
    public PerformancePersonnelDto getPerformancePersonnel(Long personnelId,
                                                            LocalDate dateDebut,
                                                            LocalDate dateFin) {
        Personnel personnel = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        LocalDate from = (dateDebut != null) ? dateDebut : LocalDate.now(clock).minusDays(KPI_TREND_DAYS);
        LocalDate to = (dateFin != null) ? dateFin : LocalDate.now(clock);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("error.menage.statistiques.periodeInvalide");
        }

        long assignees = tacheRepository
                .countByPersonnelIdAndDatePlanifieeBetween(personnelId, from, to);
        long terminees = tacheRepository
                .countByPersonnelIdAndDatePlanifieeBetweenAndStatut(personnelId, from, to, StatutTache.TERMINEE);
        long annulees = tacheRepository
                .countByPersonnelIdAndDatePlanifieeBetweenAndStatut(personnelId, from, to, StatutTache.ANNULEE);

        Double tempsMoyen = averageDurationMinutes(
                tacheRepository.findTermineesAvecDureeForPersonnel(personnelId, from, to));

        Double taux = (assignees > 0) ? (terminees * 100.0) / assignees : 0.0;

        return new PerformancePersonnelDto(
                personnelId, personnel.getNomComplet(),
                from, to,
                assignees, terminees, annulees,
                tempsMoyen, taux);
    }

    @Override
    public KpiMenageDto getKpi() {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        long nbActif = personnelRepository.countByActifTrue();
        long nbJour = tacheRepository.countByDatePlanifiee(today);
        long nbTerminees = tacheRepository
                .countByDatePlanifieeAndStatut(today, StatutTache.TERMINEE);
        long nbRetard = tacheRepository.findTopEnRetard(today, now, PageRequest.of(0, Integer.MAX_VALUE))
                .size(); // V1 — pas de count() dedie ; volume faible

        double tauxJour = (nbJour > 0) ? (nbTerminees * 100.0) / nbJour : 0.0;

        LocalDate trendFrom = today.minusDays(KPI_TREND_DAYS);
        Double tempsMoyen30 = averageDurationMinutes(
                tacheRepository.findTermineesAvecDureeOnPeriode(trendFrom, today));

        return new KpiMenageDto(today, nbActif, nbJour, (long) nbRetard, tauxJour, tempsMoyen30);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers internes
    // ─────────────────────────────────────────────────────────────────

    /**
     * Calcule les statistiques pour une periode [from, to] inclusive.
     * {@code referenceDate} sert au champ {@code dateReference} du DTO
     * (typiquement {@code from} pour une periode multi-jour ou la date
     * unique pour le jour).
     */
    private StatistiquesMenageDto computeStatistiques(LocalDate referenceDate,
                                                       LocalDate from,
                                                       LocalDate to) {
        boolean singleDay = from.equals(to);
        long nbActif = personnelRepository.countByActifTrue();

        long total = singleDay
                ? tacheRepository.countByDatePlanifiee(from)
                : tacheRepository.countByDatePlanifieeBetween(from, to);
        long enCours = singleDay
                ? tacheRepository.countByDatePlanifieeAndStatut(from, StatutTache.EN_COURS)
                : tacheRepository.countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.EN_COURS);
        long terminees = singleDay
                ? tacheRepository.countByDatePlanifieeAndStatut(from, StatutTache.TERMINEE)
                : tacheRepository.countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.TERMINEE);
        long urgentes = singleDay
                ? tacheRepository.countByDatePlanifieeAndPriorite(from, PRIORITE_CRITIQUE)
                : countByPrioriteOnPeriode(from, to, PRIORITE_CRITIQUE);

        // En retard : on prend les "non terminees ET non annulees ET date < today"
        // pour la periode, plus "date = today ET heureFinPrevue < now" si la
        // periode inclut today.
        long enRetard = countEnRetardOnPeriode(from, to);

        Map<String, Long> repStatut = singleDay
                ? toStringLongMap(tacheRepository.countByStatutForDate(from))
                : toStringLongMap(tacheRepository.countByStatutForPeriode(from, to));
        Map<String, Long> repType = singleDay
                ? toStringLongMap(tacheRepository.countByTypeForDate(from))
                : toStringLongMap(tacheRepository.countByTypeForPeriode(from, to));
        Map<String, Long> repPriorite = singleDay
                ? toIntegerKeyMap(tacheRepository.countByPrioriteForDate(from))
                : toIntegerKeyMap(tacheRepository.countByPrioriteForPeriode(from, to));

        Double tempsMoyen = averageDurationMinutes(
                tacheRepository.findTermineesAvecDureeOnPeriode(from, to));

        Double taux = (total > 0) ? (terminees * 100.0) / total : 0.0;

        return new StatistiquesMenageDto(
                referenceDate,
                nbActif,
                total,
                enCours,
                terminees,
                enRetard,
                repStatut,
                repType,
                repPriorite,
                tempsMoyen,
                taux,
                urgentes,
                0L); // nombreConflitsPlanning : non implemente V1
    }

    /**
     * Carte compacte d'une tache (jointure Chambre pour le numero).
     * Best-effort : si la chambre n'existe plus, numeroChambre = null.
     */
    private TacheCarteDto toCarte(Tache tache) {
        String numeroChambre = chambreRepository.findById(tache.getChambreId())
                .map(Chambre::getNumeroChambre)
                .orElse(null);
        String libelleStatut = libelleStatut(tache.getStatut());
        return new TacheCarteDto(tache.getTacheId(), numeroChambre, libelleStatut);
    }

    /** Aligne avec {@code TacheMapper.resolveLibelleStatut}. */
    private String libelleStatut(StatutTache statut) {
        if (statut == null) {
            return null;
        }
        if (statut == StatutTache.PLANIFIEE) return "Planifiée";
        if (statut == StatutTache.EN_COURS) return "En cours";
        if (statut == StatutTache.TERMINEE) return "Terminée";
        if (statut == StatutTache.ANNULEE) return "Annulée";
        return null;
    }

    /**
     * Convertit le resultat d'une query {@code SELECT enum, COUNT(*)} en
     * {@code Map<String, Long>} avec {@code enum.name()} comme cle.
     */
    private Map<String, Long> toStringLongMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                String key = (row[0] instanceof Enum<?> e) ? e.name() : row[0].toString();
                Long value = (row[1] instanceof Number n) ? n.longValue() : 0L;
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Convertit le resultat d'une query {@code SELECT integer, COUNT(*)} en
     * {@code Map<String, Long>} (la cle entiere est convertie en {@code String}).
     */
    private Map<String, Long> toIntegerKeyMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                String key = row[0].toString();
                Long value = (row[1] instanceof Number n) ? n.longValue() : 0L;
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Moyenne en minutes de {@code Duration.between(heureDebutReelle,
     * heureFinReelle)} sur les taches fournies. Retourne {@code null} si
     * aucune tache (evite la division par zero et le NaN cote front).
     */
    private Double averageDurationMinutes(List<Tache> taches) {
        if (taches == null || taches.isEmpty()) {
            return null;
        }
        long totalMinutes = 0L;
        int count = 0;
        for (Tache t : taches) {
            if (t.getHeureDebutReelle() != null && t.getHeureFinReelle() != null) {
                totalMinutes += Duration.between(t.getHeureDebutReelle(), t.getHeureFinReelle()).toMinutes();
                count++;
            }
        }
        return (count > 0) ? (double) totalMinutes / count : null;
    }

    /**
     * Count des taches en retard sur une periode (best-effort : itere
     * sur la liste retournee par findTopEnRetard avec un Pageable large).
     * V1 — pas de COUNT() dedie cote repo, le volume reste faible.
     */
    private long countEnRetardOnPeriode(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        if (to.isBefore(today)) {
            // La periode est entierement passee : toutes les non terminees / non annulees comptent.
            long planifiees = tacheRepository
                    .countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.PLANIFIEE);
            long enCours = tacheRepository
                    .countByDatePlanifieeBetweenAndStatut(from, to, StatutTache.EN_COURS);
            return planifiees + enCours;
        }
        // Periode incluant ou apres today : on utilise la query findTopEnRetard
        // pour ne compter que ce qui est *reellement* en retard a cette heure.
        return tacheRepository
                .findTopEnRetard(today, now, PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .filter(t -> !t.getDatePlanifiee().isBefore(from)
                        && !t.getDatePlanifiee().isAfter(to))
                .count();
    }

    /**
     * Helper pour count par priorite sur une periode (extrait du group by).
     * Le repo n'expose pas {@code countByDatePlanifieeBetweenAndPriorite}
     * pour limiter le nombre de methodes ; on filtre la map.
     */
    private long countByPrioriteOnPeriode(LocalDate from, LocalDate to, int priorite) {
        return toIntegerKeyMap(tacheRepository.countByPrioriteForPeriode(from, to))
                .getOrDefault(String.valueOf(priorite), 0L);
    }
}
