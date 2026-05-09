package com.cityprojects.citybackend.dto.menage;

import java.time.Instant;

/**
 * DTO de sortie pour
 * {@link com.cityprojects.citybackend.entity.menage.Historique}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext).</p>
 */
public record HistoriqueDto(
        Long historiqueId,
        Long tacheId,
        Long chambreId,
        Long personnelId,
        String action,
        String ancienStatut,
        String nouveauStatut,
        String commentaire,
        Long userId,
        Instant timestampAction) {
}
