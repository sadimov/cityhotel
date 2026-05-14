package com.cityprojects.citybackend.dto.menage;

/**
 * Carte compacte d'un personnel pour les listes courtes du Dashboard
 * menage (sous-tour C).
 *
 * <p>Utilise pour {@code DashboardMenageDto.personnelsDisponibles}. Evite
 * de serialiser un {@link PersonnelDto} complet quand on a juste besoin
 * de l'id + le nom complet pour afficher une vignette.</p>
 */
public record PersonnelCarteDto(
        Long personnelId,
        String nomComplet) {
}
