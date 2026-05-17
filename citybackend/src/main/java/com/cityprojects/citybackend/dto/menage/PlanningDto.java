package com.cityprojects.citybackend.dto.menage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.menage.Planning}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext).</p>
 */
public record PlanningDto(
        Long planningId,
        Long personnelId,
        LocalDate dateTravail,
        LocalTime heureDebut,
        LocalTime heureFin,
        Boolean disponible,
        String commentaires,
        Instant createdAt,
        Instant updatedAt,
        /** Nom complet du personnel (résolu côté service). */
        String nomPersonnel) {

    public PlanningDto withResolvedNames(String nomPers) {
        return new PlanningDto(
                planningId, personnelId, dateTravail, heureDebut, heureFin,
                disponible, commentaires, createdAt, updatedAt, nomPers);
    }
}
