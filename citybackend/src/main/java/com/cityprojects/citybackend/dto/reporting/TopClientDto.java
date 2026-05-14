package com.cityprojects.citybackend.dto.reporting;

import java.math.BigDecimal;

/**
 * Rapport R-CLI-001 — Top clients sur une periode.
 *
 * <p>Classement par {@code caTtc} decroissant (tri-breaker : {@code nbNuitees} desc).
 * Source : factures EMISE/PARTIELLEMENT_PAYEE/PAYEE rattachees a un client + nuitees
 * de ses reservations sur la periode.</p>
 *
 * @param rang        rang dans le classement (1 = meilleur)
 * @param clientId    FK client
 * @param numeroClient code metier
 * @param nom         nom famille
 * @param prenom      prenom
 * @param nbNuitees   total nuits consommees / facturees sur la periode
 * @param nbFactures  total factures (hors ANNULEE)
 * @param caTtc       montant total facture (somme {@code montant_ttc})
 * @param caPaye      montant total deja paye (somme {@code montant_paye})
 */
public record TopClientDto(
        Integer rang,
        Long clientId,
        String numeroClient,
        String nom,
        String prenom,
        Long nbNuitees,
        Long nbFactures,
        BigDecimal caTtc,
        BigDecimal caPaye
) {
}
