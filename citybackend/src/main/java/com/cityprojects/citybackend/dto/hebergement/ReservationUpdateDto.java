package com.cityprojects.citybackend.dto.hebergement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO d'entrée pour la <b>modification partielle</b> d'une réservation
 * existante ({@code PUT /api/hebergement/reservations/{id}}).
 *
 * <h2>Pourquoi un DTO séparé de {@link ReservationCreateDto} ?</h2>
 * <ul>
 *   <li>À la création, des champs comme {@code clientPrincipalId},
 *       {@code dateArrivee}, {@code dateDepart}, {@code chambres} sont
 *       obligatoires.</li>
 *   <li>À la modification, on doit pouvoir n'envoyer que ce qui change
 *       (changement de client, ajustement de dates, etc.). Réutiliser
 *       {@link ReservationCreateDto} avec ses {@code @NotNull}/{@code @NotEmpty}
 *       imposait au front de renvoyer un payload complet, sinon Jakarta
 *       Validation retournait 400 {@code error.validation.failed}.</li>
 *   <li>Les contraintes {@code @Future}/{@code @FutureOrPresent} sur les dates
 *       sont retirées ici : une réservation déjà active peut être éditée
 *       (le verrou métier {@code error.reservation.update.terminated} est
 *       côté service).</li>
 * </ul>
 *
 * <h2>Sémantique des champs null</h2>
 * <p>Un champ {@code null} signifie <b>"ne pas toucher"</b> côté service.
 * Cas particulier : {@code societeId = -1L} (sentinelle) signifie
 * <b>"détacher la société"</b> (cf. {@link com.cityprojects.citybackend.service.hebergement.ReservationServiceImpl#update}).</p>
 *
 * <p><b>Aucun {@code hotelId} ni {@code userId}</b> : isolation tenant via JWT.</p>
 *
 * <p><b>Les chambres ne sont PAS modifiables ici</b> — utiliser l'endpoint
 * dédié {@code POST /{id}/chambre} (changement de chambre avec traçabilité).</p>
 */
public record ReservationUpdateDto(
        Long clientPrincipalId,

        Long societeId,

        LocalDate dateArrivee,

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

        @Size(max = 50, message = "error.reservation.sourceCanal.tooLong")
        String sourceCanal) {
}
