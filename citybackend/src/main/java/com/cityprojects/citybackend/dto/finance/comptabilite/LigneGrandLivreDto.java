package com.cityprojects.citybackend.dto.finance.comptabilite;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne du grand livre - une ligne d'ecriture pour un compte donne (B5).
 *
 * <p>{@code soldeProgressif} : solde courant du compte apres prise en compte
 * de cette ligne (inclut le report initial + toutes les lignes precedentes
 * dans l'ordre chronologique). Convention : positif si solde debiteur, negatif
 * si solde crediteur (independant du sens normal).</p>
 */
public record LigneGrandLivreDto(
        LocalDate dateComptable,
        String numeroEcriture,
        String journalCode,
        String libelleEcriture,
        String reference,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal soldeProgressif) {
}
