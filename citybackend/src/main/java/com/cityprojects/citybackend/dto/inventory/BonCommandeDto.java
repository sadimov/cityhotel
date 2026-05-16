package com.cityprojects.citybackend.dto.inventory;

import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.inventory.BonCommande}.
 *
 * <p>Inclut les lignes pour eviter un appel HTTP supplementaire cote front
 * (les BC ont peu de lignes en pratique - quelques unites a quelques dizaines).</p>
 */
public record BonCommandeDto(
        Long bonCommandeId,
        String numeroBc,
        Long fournisseurId,
        StatutBonCommande statut,
        LocalDate dateCommande,
        LocalDate dateLivraisonPrevue,
        LocalDate dateLivraisonReelle,
        BigDecimal montantTotal,
        BigDecimal montantTva,
        String commentaires,
        Long userId,
        List<LigneBonCommandeDto> lignes,
        Instant createdAt,
        Instant updatedAt,
        /** Nom du fournisseur (résolu côté service, anti-N+1). */
        String nomFournisseur) {

    /** Reconstruit le DTO en injectant le nom du fournisseur résolu. */
    public BonCommandeDto withResolvedNames(String nomFour) {
        return new BonCommandeDto(
                bonCommandeId, numeroBc, fournisseurId, statut,
                dateCommande, dateLivraisonPrevue, dateLivraisonReelle,
                montantTotal, montantTva, commentaires, userId, lignes,
                createdAt, updatedAt, nomFour);
    }
}
