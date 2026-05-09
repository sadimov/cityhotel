package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.finance.ModePaiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO de sortie pour la cloture de caisse journaliere ("Z de caisse", Tour 26.1).
 *
 * <p>Decision arbitree (3=i) : agregat en lecture seule a la demande, AUCUNE
 * persistance. Si on veut historiser plus tard, ce sera une nouvelle table
 * {@code restaurant.clotures_caisse} avec un service dedie.</p>
 *
 * <h3>Source des donnees</h3>
 * <p>Tous les paiements en statut {@code VALIDE} dont {@code datePaiement = date}
 * pour le tenant courant. Les paiements {@code ANNULE / EN_ATTENTE / REFUSE} sont
 * exclus du total caisse (audit comptable seulement).</p>
 *
 * <h3>Cross-tenant</h3>
 * <p>Le service applique {@code TenantContext} via Hibernate {@code @TenantId}
 * sur les paiements ; aucun risque de fuite. {@code hotelId} est repris du
 * contexte pour information.</p>
 *
 * @param date date de la cloture (jour observe)
 * @param hotelId hotel observe (issu du TenantContext, jamais d'un parametre client)
 * @param totauxParMode pour chaque mode de paiement utilise : (montant total, nombre)
 * @param totalGlobal somme des montants tous modes confondus (= encaissements de la journee)
 * @param nbTransactionsTotal nombre total de paiements VALIDES (= sum(nombre) sur totauxParMode)
 * @param nbCommandesEncaissees nombre de commandes du jour avec factureId set (encaissees comptant)
 * @param nbCommandesAnnulees nombre de commandes ANNULEE creees ce jour-la
 * @param generatedAt instant exact ou la cloture a ete calculee (cote serveur)
 */
public record ClotureCaisseDto(
        LocalDate date,
        Long hotelId,
        Map<ModePaiement, MontantNbPair> totauxParMode,
        BigDecimal totalGlobal,
        Integer nbTransactionsTotal,
        Integer nbCommandesEncaissees,
        Integer nbCommandesAnnulees,
        Instant generatedAt) {

    /**
     * Couple (montant, nombre) pour un mode de paiement donne.
     */
    public record MontantNbPair(BigDecimal montant, Integer nombre) {
    }
}
