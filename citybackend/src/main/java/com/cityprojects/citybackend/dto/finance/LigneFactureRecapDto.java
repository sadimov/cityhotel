package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vue tronquee d'une ligne de facture pour la modale "Paiements" du calendrier
 * (Tour 45 — fix dette technique {@code lignesFromRecap}).
 *
 * <p>Sert l'endpoint {@code GET /api/finance/factures/lignes-by-reservation/{id}}
 * et remplace le proxy {@code factureId -> ligneFactureId} qui faussait le
 * paiement granulaire cote front : le front envoyait des factureId comme
 * ligneFactureId au backend, ce qui pouvait provoquer
 * {@code LigneFactureRepository.findById(...)} sur la mauvaise entite.</p>
 *
 * <p>Le champ {@code description} reprend {@link com.cityprojects.citybackend.entity.finance.LigneFacture#getLibelle()}
 * ; fallback {@code "Ligne #" + ligneFactureId} si null.</p>
 *
 * <p>Le champ {@code dateLigne} est calcule cote service :
 * <ul>
 *   <li>{@code NUITEE} : {@code Nuitee.dateNuit} via lookup {@code NuiteeRepository}.</li>
 *   <li>autres types : {@code datePrestation} de la ligne si renseignee, sinon {@code null}.</li>
 * </ul>
 * Le front affiche un fallback si null (cf. spec Tour 45).</p>
 *
 * <p>{@code montantPaye} = somme {@code AffectationPaiement.montantAffecte}
 * filtree par {@code ligneFactureId} (champ ajoute Tour 45 changeset 034).
 * {@code reste = montantTtc - montantPaye} (clamp >= 0 cote service).</p>
 */
public record LigneFactureRecapDto(
        Long ligneFactureId,
        Long factureId,
        String factureNumero,
        String description,
        TypeLigneFacture typeLigne,
        LocalDate dateLigne,
        BigDecimal montantTtc,
        BigDecimal montantPaye,
        BigDecimal reste) {
}
