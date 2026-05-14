package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.DashboardMenageDto;
import com.cityprojects.citybackend.dto.menage.KpiMenageDto;
import com.cityprojects.citybackend.dto.menage.PerformancePersonnelDto;
import com.cityprojects.citybackend.dto.menage.StatistiquesMenageDto;

import java.time.LocalDate;

/**
 * Service de tableau de bord / statistiques / KPI du module menage
 * (sous-tour C — cf. {@code endpoints_module_menage.txt} §Dashboard).
 *
 * <p>Read-only : toutes les methodes sont des agregats (count, group by,
 * moyennes). Aucun effet de bord sur la base.</p>
 *
 * <p>Multi-tenant : applique automatiquement via {@code @TenantId} sur
 * {@code Tache} et {@code Personnel}. Le service est annote
 * {@code @RequireTenant} cote impl.</p>
 */
public interface MenageDashboardService {

    /**
     * Tableau de bord temps reel du jour : statistiques + top 5 taches en
     * retard + personnels disponibles aujourd'hui.
     *
     * <p>Endpoint cible : {@code GET /api/menage/dashboard}.</p>
     */
    DashboardMenageDto getDashboard();

    /**
     * Statistiques agregees pour la journee courante (alias de
     * {@code getStatistiquesPeriode(today, today)}).
     *
     * <p>Endpoint cible : {@code GET /api/menage/statistiques}.</p>
     */
    StatistiquesMenageDto getStatistiquesJour();

    /**
     * Statistiques agregees pour une periode donnee.
     *
     * <p>Endpoint cible :
     * {@code GET /api/menage/statistiques/periode?dateDebut=&dateFin=}.</p>
     */
    StatistiquesMenageDto getStatistiquesPeriode(LocalDate dateDebut, LocalDate dateFin);

    /**
     * Statistiques de performance d'un personnel sur une periode donnee.
     *
     * <p>Endpoint cible :
     * {@code GET /api/menage/statistiques/personnel/{personnelId}?dateDebut=&dateFin=}.</p>
     */
    PerformancePersonnelDto getPerformancePersonnel(Long personnelId,
                                                     LocalDate dateDebut,
                                                     LocalDate dateFin);

    /**
     * Indicateurs synthese vue tendance.
     *
     * <p>Endpoint cible : {@code GET /api/menage/kpi}. Calcul du temps de
     * realisation moyen sur les 30 derniers jours.</p>
     */
    KpiMenageDto getKpi();
}
