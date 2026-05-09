package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload du POST {@code /api/hebergement/reservations/rechercher-disponibilite}
 * et entree du service {@link com.cityprojects.citybackend.service.hebergement.ReservationService#rechercherDisponibilite}.
 *
 * <p>Recherche les chambres disponibles dans la periode
 * {@code [dateDebut, dateFin)}. {@code nbPersonnes} optionnel : si fourni,
 * filtre les chambres dont {@code nbPersonnesMax >= nbPersonnes}.</p>
 *
 * <p>Note Tour 14 (hors scope finance) : le service ne joint pas avec
 * {@code TarifChambre} pour calculer un tarif suggere — different d'une
 * iteration future.</p>
 */
public record RechercheDisponibiliteRequest(
        @NotNull(message = "error.disponibilite.dateDebut.required")
        LocalDate dateDebut,

        @NotNull(message = "error.disponibilite.dateFin.required")
        LocalDate dateFin,

        Integer nbPersonnes) {
}
