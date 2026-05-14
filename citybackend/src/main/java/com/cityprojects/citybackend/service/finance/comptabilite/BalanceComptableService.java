package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceComptableDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceFilterDto;

/**
 * Service de calcul et d'export de la balance comptable (B5).
 *
 * <p>Operation tenant-scopee ({@code @RequireTenant}). Pour chaque compte du
 * PCG ayant au moins une ligne d'ecriture sur la periode :</p>
 * <ul>
 *   <li>{@code totalDebit} / {@code totalCredit} : somme des mouvements ;</li>
 *   <li>{@code soldeDebiteur} / {@code soldeCrediteur} : ecart calcule selon
 *       le sens normal du compte (cf. {@link com.cityprojects.citybackend.entity.finance.SensNormal}).</li>
 * </ul>
 *
 * <p>Erreurs :</p>
 * <ul>
 *   <li>{@code error.etat.filterRequired} si ni exerciceId ni dateDebut/dateFin
 *       fournis ;</li>
 *   <li>{@code error.etat.dateRangeInvalide} si dateFin avant dateDebut ;</li>
 *   <li>{@code error.exercice.notFound} si exerciceId fourni inexistant ;</li>
 *   <li>{@code error.etat.classeInvalide} si classe hors 1-7.</li>
 * </ul>
 */
public interface BalanceComptableService {

    /** Calcule la balance comptable selon le filtre. */
    BalanceComptableDto compute(BalanceFilterDto filter);

    /** Export XLSX (1 feuille). */
    byte[] exportXlsx(BalanceFilterDto filter);

    /** Export PDF (paysage). */
    byte[] exportPdf(BalanceFilterDto filter);
}
