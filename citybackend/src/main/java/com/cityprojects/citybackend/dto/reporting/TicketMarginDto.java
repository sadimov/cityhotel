package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-RES-003 — Ticket moyen + marge par article (Tour 41 P2).
 *
 * <p>Ticket moyen = {@code SUM(commandes.montantTtc) / COUNT(commandes)} sur la
 * plage. Marge article = {@code prix_vente − Σ(recette.quantiteParUnite × Produit.prixUnitaire)}
 * (palier 1 : pas de {@code prixUnitaireMoyen}, fallback sur {@code prixUnitaire}).</p>
 *
 * @param from        borne inclusive
 * @param to          borne exclusive
 * @param nbCommandes commandes prises en compte (statut != ANNULEE)
 * @param caTotal     somme TTC
 * @param ticketMoyen TTC / nb
 * @param marges      detail par article
 */
public record TicketMarginDto(
        LocalDate from,
        LocalDate to,
        Long nbCommandes,
        BigDecimal caTotal,
        BigDecimal ticketMoyen,
        List<ArticleMargeDto> marges
) {

    /**
     * Marge par article.
     *
     * @param articleId         FK
     * @param libelle           snapshot
     * @param prixVente         prix de vente unitaire ArticleMenu
     * @param coutMatiere       somme recettes valorisees au prixUnitaire produit
     * @param margeUnitaire     prixVente - coutMatiere
     * @param margePourcentage  (margeUnitaire / prixVente) * 100
     */
    public record ArticleMargeDto(
            Long articleId,
            String libelle,
            BigDecimal prixVente,
            BigDecimal coutMatiere,
            BigDecimal margeUnitaire,
            BigDecimal margePourcentage
    ) {
    }
}
