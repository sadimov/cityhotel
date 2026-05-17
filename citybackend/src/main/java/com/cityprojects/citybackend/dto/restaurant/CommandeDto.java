package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.restaurant.Commande}.
 *
 * <p>Ne contient pas {@code hotelId} (resolu via TenantContext, jamais expose).</p>
 */
public record CommandeDto(
        Long commandeId,
        String numeroCommande,
        Long clientId,
        Long reservationId,
        Long factureId,
        ModeReglementCommande modeReglement,
        StatutCommande statut,
        BigDecimal montantHt,
        BigDecimal montantTtc,
        BigDecimal montantPaye,
        String devise,
        Instant dateCommande,
        String motifAnnulation,
        /** Numero de table physique (Tour 26.1, optionnel, ex. "T12"). */
        String numeroTable,
        List<LigneCommandeDto> lignes,
        Instant createdAt,
        Instant updatedAt,
        /** Nom complet du client (résolu côté service). */
        String nomClient,
        /** Numéro de réservation (résolu côté service, pour report chambre). */
        String numeroReservation,
        /** Numéro de facture (résolu côté service, si encaissé). */
        String numeroFacture) {

    public CommandeDto withResolvedNames(String nomCli, String numRes, String numFact) {
        return new CommandeDto(
                commandeId, numeroCommande, clientId, reservationId, factureId,
                modeReglement, statut, montantHt, montantTtc, montantPaye, devise,
                dateCommande, motifAnnulation, numeroTable, lignes, createdAt, updatedAt,
                nomCli, numRes, numFact);
    }
}
