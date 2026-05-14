package com.cityprojects.citybackend.dto.menage;

import java.util.List;

/**
 * Agregat synthese du Dashboard menage (sous-tour C).
 *
 * <p>Endpoint cible : {@code GET /api/menage/dashboard}.</p>
 *
 * <p>Alignement strict avec le modele frontend
 * {@code cityfrontend/src/app/features/menage/models/statistiques-menage.model.ts}
 * {@code DashboardMenage}.</p>
 *
 * @param statistiques            stats du jour (cf. {@link StatistiquesMenageDto})
 * @param tachesEnRetard          top 5 des taches en retard (vignettes
 *                                cliquables sur la page d'accueil)
 * @param personnelsDisponibles   agents actifs ET planifies disponibles
 *                                aujourd'hui (Personnel x Planning)
 */
public record DashboardMenageDto(
        StatistiquesMenageDto statistiques,
        List<TacheCarteDto> tachesEnRetard,
        List<PersonnelCarteDto> personnelsDisponibles) {
}
