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
        Instant timestampAction,
        /** Nom complet du personnel (résolu côté service). */
        String nomPersonnel,
        /** Numéro de chambre (résolu côté service). */
        String numeroChambre) {

    public HistoriqueDto withResolvedNames(String nomPers, String numChambre) {
        return new HistoriqueDto(
                historiqueId, tacheId, chambreId, personnelId, action,
                ancienStatut, nouveauStatut, commentaire, userId, timestampAction,
                nomPers, numChambre);
    }
}
