package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO d'entree pour la creation d'une reservation.
 *
 * <p><b>Aucun {@code hotelId}</b> ni {@code numeroReservation} dans le payload :
 * ils sont calcules cote serveur (TenantContext + NumerotationService.next(RES)).
 * <b>Aucun {@code userId}</b> non plus : extrait du JWT/SecurityContext.</p>
 */
public record ReservationCreateDto(
        @NotNull(message = "error.reservation.clientPrincipal.required")
        Long clientPrincipalId,

        Long societeId,

        @NotNull(message = "error.reservation.dateArrivee.required")
        @FutureOrPresent(message = "error.reservation.dateArrivee.past")
        LocalDate dateArrivee,

        @NotNull(message = "error.reservation.dateDepart.required")
        @Future(message = "error.reservation.dateDepart.notFuture")
        LocalDate dateDepart,

        @PositiveOrZero(message = "error.reservation.nbAdultes.negative")
        Integer nbAdultes,

        @PositiveOrZero(message = "error.reservation.nbEnfants.negative")
        Integer nbEnfants,

        @Size(max = 100, message = "error.reservation.motifSejour.tooLong")
        String motifSejour,

        String commentaires,

        @DecimalMin(value = "0.00", message = "error.reservation.reduction.negative")
        @DecimalMax(value = "100.00", message = "error.reservation.reduction.tooHigh")
        BigDecimal reductionPourcentage,

        @NotEmpty(message = "error.reservation.chambres.empty")
        @Valid
        List<ReservationChambreCreateDto> chambres,

        @Valid
        List<ReservationClientCreateDto> clientsAdditionnels,

        /**
         * Canal de distribution (Tour 41, R-HEB-004). Optionnel. Valeurs indicatives :
         * DIRECT_HOTEL, WEBSITE, BOOKING_COM, EXPEDIA, AIRBNB, AGENCE_VOYAGE,
         * WALK_IN, TELEPHONE, EMAIL, AUTRE. Place en dernier pour preserver la
         * compatibilite des appels positionnels existants (tests Tours 8+).
         */
        @Size(max = 50, message = "error.reservation.sourceCanal.tooLong")
        String sourceCanal) {

    /**
     * Constructeur compact retro-compatible : permet d'instancier sans le champ
     * {@code sourceCanal} ajoute au Tour 41 — evite la refactor en cascade des
     * tests historiques. Equivalent a {@code sourceCanal = null}.
     */
    public ReservationCreateDto(
            Long clientPrincipalId,
            Long societeId,
            LocalDate dateArrivee,
            LocalDate dateDepart,
            Integer nbAdultes,
            Integer nbEnfants,
            String motifSejour,
            String commentaires,
            BigDecimal reductionPourcentage,
            List<ReservationChambreCreateDto> chambres,
            List<ReservationClientCreateDto> clientsAdditionnels) {
        this(clientPrincipalId, societeId, dateArrivee, dateDepart, nbAdultes,
                nbEnfants, motifSejour, commentaires, reductionPourcentage,
                chambres, clientsAdditionnels, null);
    }
}
