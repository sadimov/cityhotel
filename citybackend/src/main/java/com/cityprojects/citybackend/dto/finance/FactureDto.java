package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.finance.Facture}.
 *
 * <p>Inclut les lignes pour eviter un appel HTTP supplementaire (les factures
 * ont rarement plus de quelques dizaines de lignes en pratique).</p>
 *
 * <p>{@code montantRestant} est calcule = {@code montantTtc - montantPaye}.</p>
 *
 * <p>{@code hotelId} n'est PAS expose (regle CLAUDE.md).</p>
 */
public record FactureDto(
        Long factureId,
        String numeroFacture,
        TypeFacture typeFacture,
        Long compteId,
        Long clientId,
        Long societeId,
        Long reservationId,
        Long fournisseurId,
        Long factureReferenceId,
        LocalDate dateFacture,
        LocalDate dateEcheance,
        BigDecimal montantHt,
        BigDecimal montantTva,
        BigDecimal montantTtc,
        BigDecimal montantPaye,
        BigDecimal montantRestant,
        StatutFacture statut,
        String devise,
        String commentaires,
        Long userId,
        List<LigneFactureDto> lignes,
        Instant createdAt,
        Instant updatedAt,
        /** Nom complet du client (résolu côté service). */
        String nomClient,
        /** Raison sociale de la société (résolue côté service). */
        String nomSociete,
        /** Nom du fournisseur (résolu côté service, pour factures d'achat). */
        String nomFournisseur,
        /** Numéro réservation (résolu côté service, pour factures d'hébergement). */
        String numeroReservation) {

    /**
     * Reconstruit un {@link FactureDto} avec les noms résolus injectés
     * (pattern anti-N+1 via batch lookup côté service).
     */
    public FactureDto withResolvedNames(String nomCli, String nomSoc,
                                        String nomFour, String numeroRes) {
        return new FactureDto(
                factureId, numeroFacture, typeFacture, compteId, clientId, societeId,
                reservationId, fournisseurId, factureReferenceId, dateFacture, dateEcheance,
                montantHt, montantTva, montantTtc, montantPaye, montantRestant, statut,
                devise, commentaires, userId, lignes, createdAt, updatedAt,
                nomCli, nomSoc, nomFour, numeroRes);
    }
}
