package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rapport R-FIN-001 — Recap chiffre d'affaires sur une periode.
 *
 * <p>Source : {@code finance.factures} (CA emis) + {@code finance.paiements} (encaissement).
 * Filtre statut : exclut ANNULEE pour le CA emis ; inclut tous les paiements VALIDE.</p>
 *
 * @param from               borne inclusive
 * @param to                 borne exclusive
 * @param nbFactures         nombre de factures emises (hors ANNULEE)
 * @param caEmisHt           somme {@code montant_ht} sur la periode
 * @param caEmisTva          somme {@code montant_tva}
 * @param caEmisTtc          somme {@code montant_ttc}
 * @param caPayeTtc          somme {@code montant_paye} sur les factures emises sur la periode
 * @param nbPaiements        nombre de paiements VALIDE encaisses sur la periode
 * @param montantEncaisse    somme {@code montant_total} des paiements VALIDE sur la periode
 * @param devise             devise (constante "MRU" pour le palier 1)
 */
public record CARecapDto(
        LocalDate from,
        LocalDate to,
        Long nbFactures,
        BigDecimal caEmisHt,
        BigDecimal caEmisTva,
        BigDecimal caEmisTtc,
        BigDecimal caPayeTtc,
        Long nbPaiements,
        BigDecimal montantEncaisse,
        String devise
) {
}
