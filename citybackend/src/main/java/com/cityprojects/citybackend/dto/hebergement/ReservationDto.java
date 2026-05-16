package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.StatutReservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de sortie pour
 * {@link com.cityprojects.citybackend.entity.hebergement.Reservation}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 *
 * <p>Champs {@code nomClientPrincipal} / {@code nomSociete} : enrichis
 * par le service via batch lookup (anti-N+1). Permettent au front d'afficher
 * directement le nom sans recharger Client/Societe — pattern à répliquer
 * pour les autres DTOs avec FK numériques (Facture, BonCommande, etc.).</p>
 */
public record ReservationDto(
        Long reservationId,
        String numeroReservation,
        Long clientPrincipalId,
        Long societeId,
        LocalDate dateArrivee,
        LocalDate dateDepart,
        Integer nbNuits,
        Integer nbAdultes,
        Integer nbEnfants,
        StatutReservation statut,
        String motifSejour,
        String commentaires,
        BigDecimal reductionPourcentage,
        BigDecimal montantTotal,
        Long userId,
        Instant createdAt,
        Instant updatedAt,
        /**
         * Pivots {@code reservation_chambres} associés. Liste vide si non
         * hydratée. Indispensable au front (calendrier) pour positionner les
         * rectangles de réservation sur la bonne ligne chambre.
         */
        List<ReservationChambreDto> chambres,
        /** Canal de distribution (Tour 41, R-HEB-004). Place en dernier. */
        String sourceCanal,
        /** Nom complet du client principal (résolu côté service). */
        String nomClientPrincipal,
        /** Raison sociale de la société (résolue côté service). */
        String nomSociete) {

    /**
     * Reconstruit un {@link ReservationDto} en injectant les noms résolus,
     * sans dupliquer tous les autres champs. Utile pour anti-N+1 batch lookup.
     */
    public ReservationDto withResolvedNames(String nomClient, String nomSoc) {
        return new ReservationDto(
                reservationId, numeroReservation, clientPrincipalId, societeId,
                dateArrivee, dateDepart, nbNuits, nbAdultes, nbEnfants,
                statut, motifSejour, commentaires, reductionPourcentage, montantTotal,
                userId, createdAt, updatedAt, chambres, sourceCanal,
                nomClient, nomSoc);
    }
}
