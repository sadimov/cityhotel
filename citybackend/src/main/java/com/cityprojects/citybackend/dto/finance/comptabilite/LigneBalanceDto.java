package com.cityprojects.citybackend.dto.finance.comptabilite;

import com.cityprojects.citybackend.entity.finance.NatureCompte;
import com.cityprojects.citybackend.entity.finance.SensNormal;

import java.math.BigDecimal;

/**
 * Ligne de la balance comptable - agregat par compte sur une periode (B5).
 *
 * <p>{@code soldeDebiteur} / {@code soldeCrediteur} sont mutuellement exclusifs
 * pour les comptes de sens {@link SensNormal#DEBITEUR} et
 * {@link SensNormal#CREDITEUR} (l'un est positif, l'autre est 0). Pour les
 * comptes {@link SensNormal#MIXTE} (tresorerie), les deux peuvent etre
 * positifs - dans la pratique un seul l'est selon le solde reel mais la
 * regle reste : si D &gt; C alors solde debiteur ; si C &gt; D alors solde
 * crediteur ; si D == C alors les deux a zero.</p>
 */
public record LigneBalanceDto(
        String compteCode,
        String compteLibelle,
        int classe,
        NatureCompte nature,
        SensNormal sensNormal,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal soldeDebiteur,
        BigDecimal soldeCrediteur) {
}
