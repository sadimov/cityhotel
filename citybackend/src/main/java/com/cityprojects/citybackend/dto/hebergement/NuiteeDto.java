package com.cityprojects.citybackend.dto.hebergement;

import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.hebergement.Nuitee}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId}.</p>
 *
 * <p><b>Convention de nommage</b> (Tour 14 audit B1) : la colonne entite
 * s'appelle {@code dateNuit} mais le DTO expose {@code dateNuitee} pour
 * s'aligner avec le contrat front (source de verite : spec API du Tour 14).
 * Le mapper {@link com.cityprojects.citybackend.mapper.hebergement.ReservationMapper}
 * et le {@link com.cityprojects.citybackend.mapper.hebergement.NuiteeMapper}
 * gerent l'alias via {@code @Mapping}.</p>
 */
public record NuiteeDto(
        Long id,
        Long reservationId,
        Long chambreId,
        LocalDate dateNuitee,
        BigDecimal prixNuit,
        BigDecimal taxeSejour,
        StatutNuitee statut,
        Instant createdAt,
        /** Numéro de chambre (résolu côté service, anti-N+1). */
        String numeroChambre,
        /** Numéro de réservation (résolu côté service, anti-N+1). */
        String numeroReservation) {

    public NuiteeDto withResolvedNames(String numChambre, String numRes) {
        return new NuiteeDto(
                id, reservationId, chambreId, dateNuitee, prixNuit, taxeSejour,
                statut, createdAt, numChambre, numRes);
    }
}
