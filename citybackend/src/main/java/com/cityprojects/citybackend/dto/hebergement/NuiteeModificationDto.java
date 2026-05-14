package com.cityprojects.citybackend.dto.hebergement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie (Tour 45) : etat d'une nuitee <b>provisoire</b> (eligible a
 * modification individuelle de montant).
 *
 * <p>Une nuitee est <em>provisoire</em> si sa {@link com.cityprojects.citybackend.entity.finance.LigneFacture}
 * parente est dans un etat <b>non terminal</b> (factureParente.statut !=
 * {@code PAYEE} et != {@code ANNULEE}). Si la nuitee n'a pas encore de
 * ligne facture, elle est consideree provisoire (modification du
 * {@code Nuitee.prixNuit} seul, sans repercussion finance).</p>
 *
 * <p>Champs (cf. contrat front Tour 45 Phase B) :</p>
 * <ul>
 *   <li>{@code prixOriginal} : {@code Nuitee.prixNuit} actuel (avant modification).</li>
 *   <li>{@code montantLigneFacture} : {@code LigneFacture.montantTtc} actuel
 *       (ou {@code montantHt} si pas de TTC distinct). {@code null} si pas
 *       de ligne facture.</li>
 *   <li>{@code ligneFactureId} : FK vers la ligne facture (nullable).</li>
 *   <li>{@code operationCompteId} : FK vers l'operation DEBIT du compte
 *       client correspondant a cette ligne facture (nullable - les operations
 *       sont posees a l'emission de la facture, donc presentes des qu'une
 *       facture est emise).</li>
 *   <li>{@code statutNuitee} : PREVUE / CONSOMMEE / FACTUREE - informatif,
 *       ne filtre PAS l'eligibilite (cf. doctrine Tour 45 : seul le statut
 *       de la facture parente prime).</li>
 *   <li>{@code statutFactureParente} : BROUILLON / EMISE / PARTIELLEMENT_PAYEE
 *       / PAYEE / ANNULEE - {@code null} si pas de ligne facture.</li>
 * </ul>
 */
public record NuiteeModificationDto(
        Long nuiteeId,
        LocalDate dateNuit,
        BigDecimal prixOriginal,
        BigDecimal montantLigneFacture,
        Long ligneFactureId,
        Long operationCompteId,
        String statutNuitee,
        String statutFactureParente) {
}
