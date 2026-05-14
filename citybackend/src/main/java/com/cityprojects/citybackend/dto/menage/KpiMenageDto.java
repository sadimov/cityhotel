package com.cityprojects.citybackend.dto.menage;

import java.time.LocalDate;

/**
 * Indicateurs de performance synthese du module menage (sous-tour C).
 *
 * <p>Endpoint cible : {@code GET /api/menage/kpi}. Vue allege par rapport
 * a {@link StatistiquesMenageDto} : seuls les KPI bord de tableau sont
 * exposes (sans les distributions detaillees).</p>
 *
 * @param dateReference                  date de reference (jour courant)
 * @param nombrePersonnelActif           agents actifs
 * @param nombreTachesAujourdhui         taches du jour
 * @param nombreTachesEnRetard           taches du jour ou anterieures
 *                                       non terminees
 * @param tauxRealisationJour            pourcentage TERMINEE / TOTAL pour
 *                                       la journee courante
 * @param tempsRealisationMoyen30Jours   moyenne en minutes du temps de
 *                                       realisation sur les 30 derniers
 *                                       jours (vue tendance)
 */
public record KpiMenageDto(
        LocalDate dateReference,
        Long nombrePersonnelActif,
        Long nombreTachesAujourdhui,
        Long nombreTachesEnRetard,
        Double tauxRealisationJour,
        Double tempsRealisationMoyen30Jours) {
}
