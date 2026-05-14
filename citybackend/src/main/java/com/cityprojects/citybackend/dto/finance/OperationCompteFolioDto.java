package com.cityprojects.citybackend.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de sortie (Tour 46) — une ligne du folio compte client filtre par dates.
 *
 * <p>Represente une operation du compte auxiliaire client enrichie des libelles
 * facture / ligne facture / paiement associes. Le {@code soldeApres} est
 * recalcule par accumulation depuis le {@code soldeOuverture} du folio (donc
 * une projection contextuelle a la plage de dates demandee, distincte du
 * {@code soldeApres} stocke en base qui represente le solde chronologique
 * absolu de l'historique complet).</p>
 *
 * <p>Convention de signe (cf. {@link FolioDto}) :
 * <ul>
 *   <li>DEBIT = augmentation de la dette client (facturation -&gt; solde monte)</li>
 *   <li>CREDIT = reduction de la dette client (encaissement -&gt; solde baisse)</li>
 * </ul>
 * Le frontend Tour 46 inverse l'affichage pour montrer "client doit" / "client
 * a paye d'avance".</p>
 */
public record OperationCompteFolioDto(
        Long operationId,
        LocalDate dateOperation,
        String type,
        String motif,
        String description,
        BigDecimal montant,
        BigDecimal soldeApres,
        Long factureId,
        String factureNumero,
        Long ligneFactureId,
        String ligneLibelle,
        Long paiementId,
        String paiementNumero) {
}
