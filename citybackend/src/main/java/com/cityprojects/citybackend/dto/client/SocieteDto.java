package com.cityprojects.citybackend.dto.client;

import java.time.Instant;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.client.Societe}.
 * <p>
 * <b>NE CONTIENT PAS</b> {@code hotelId} : ce champ ne doit jamais transiter
 * cote API (l'isolation tenant est cote serveur via TenantContext, cf.
 * CLAUDE.md racine §6.1 / §10).
 *
 * @param societeId         identifiant technique
 * @param societeNom        nom de la societe
 * @param siret             numero SIRET (optionnel)
 * @param adresse           adresse postale (optionnel)
 * @param ville             ville (optionnel)
 * @param pays              pays (optionnel)
 * @param telephone         telephone (optionnel)
 * @param email             email (optionnel)
 * @param contactPrincipal  nom du contact principal (optionnel)
 * @param actif             flag d'activation
 * @param createdAt         timestamp de creation (UTC)
 * @param updatedAt         timestamp de derniere modification (UTC)
 */
public record SocieteDto(
        Long societeId,
        String societeNom,
        String siret,
        String adresse,
        String ville,
        String pays,
        String telephone,
        String email,
        String contactPrincipal,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
