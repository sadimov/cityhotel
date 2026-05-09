package com.cityprojects.citybackend.dto.client;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.client.Client}.
 * <p>
 * <b>NE CONTIENT PAS</b> {@code hotelId} : ce champ ne transite jamais cote API.
 *
 * @param clientId              identifiant technique
 * @param numeroClient          numero metier ({@code CLI-2026-MR-000123})
 * @param prenom                prenom
 * @param nom                   nom de famille
 * @param nomComplet            "{prenom} {nom}", calcule cote entite
 * @param nationaliteId         FK vers {@code core.donnees_referentielles} (ref nationalite)
 * @param telephone             telephone
 * @param email                 email
 * @param adresse               adresse postale
 * @param ville                 ville
 * @param pays                  pays
 * @param typeIdentificationId  FK vers {@code core.donnees_referentielles} (CIN, passeport, ...)
 * @param numeroIdentification  numero du document d'identite
 * @param dateNaissance         date de naissance
 * @param societeId             FK optionnelle vers {@code client.societes}
 * @param actif                 flag d'activation
 * @param createdAt             timestamp de creation (UTC)
 * @param updatedAt             timestamp de derniere modification (UTC)
 */
public record ClientDto(
        Long clientId,
        String numeroClient,
        String prenom,
        String nom,
        String nomComplet,
        Long nationaliteId,
        String telephone,
        String email,
        String adresse,
        String ville,
        String pays,
        Long typeIdentificationId,
        String numeroIdentification,
        LocalDate dateNaissance,
        Long societeId,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
