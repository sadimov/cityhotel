package com.cityprojects.citybackend.dto.menage;

import java.time.LocalDate;

/**
 * Statistiques de performance d'un personnel sur une periode (sous-tour C).
 *
 * <p>Endpoint cible :
 * {@code GET /api/menage/statistiques/personnel/{personnelId}?dateDebut=&dateFin=}.</p>
 *
 * @param personnelId               identifiant du personnel
 * @param nomComplet                "prenom + nom" (lecture seule)
 * @param dateDebut                 borne inferieure incluse
 * @param dateFin                   borne superieure incluse
 * @param nombreTachesAssignees     toutes les taches assignees sur la periode
 * @param nombreTachesTerminees     taches au statut TERMINEE
 * @param nombreTachesAnnulees      taches au statut ANNULEE
 * @param tempsRealisationMoyen     moyenne en minutes
 *                                  ({@code heureFinReelle - heureDebutReelle})
 *                                  sur les TERMINEE du personnel
 * @param tauxRealisation           pourcentage TERMINEE / ASSIGNEES
 */
public record PerformancePersonnelDto(
        Long personnelId,
        String nomComplet,
        LocalDate dateDebut,
        LocalDate dateFin,
        Long nombreTachesAssignees,
        Long nombreTachesTerminees,
        Long nombreTachesAnnulees,
        Double tempsRealisationMoyen,
        Double tauxRealisation) {
}
