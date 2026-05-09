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
        Instant updatedAt) {
}
