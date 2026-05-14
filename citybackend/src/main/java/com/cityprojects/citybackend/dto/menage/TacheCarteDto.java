package com.cityprojects.citybackend.dto.menage;

/**
 * Carte compacte d'une tache pour les listes courtes du Dashboard
 * menage (sous-tour C).
 *
 * <p>Utilise pour {@code DashboardMenageDto.tachesEnRetard} (top N). Evite
 * de serialiser un {@link TacheDto} complet quand on a juste besoin de
 * l'id + le numero de chambre + le libelle statut pour afficher une
 * vignette cliquable.</p>
 *
 * <p>Alignement avec le modele frontend
 * {@code DashboardMenage.tachesEnRetard} (cf. statistiques-menage.model.ts).</p>
 */
public record TacheCarteDto(
        Long tacheId,
        String numeroChambre,
        String libelleStatut) {
}
