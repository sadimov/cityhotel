package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.dto.finance.comptabilite.BilanDto;

import java.time.LocalDate;

/**
 * Service de calcul et d'export du bilan comptable SYSCOHADA simplifie (B5).
 *
 * <p>Bilan a une date d'arrete (typiquement fin d'exercice). Rubriques :</p>
 * <ul>
 *   <li>ACTIF : Immobilisations corporelles, Stocks, Creances, Tresorerie-Actif</li>
 *   <li>PASSIF : Capitaux propres, Dettes financieres, Dettes circulantes,
 *       Tresorerie-Passif, Resultat de l'exercice</li>
 * </ul>
 */
public interface BilanService {

    /**
     * Calcule le bilan a la {@code dateArrete}. Si {@code dateArrete} est
     * {@code null}, utilise la date de fin de l'exercice.
     */
    BilanDto compute(Long exerciceId, LocalDate dateArrete);

    /** Export XLSX (1 feuille ACTIF + 1 feuille PASSIF). */
    byte[] exportXlsx(Long exerciceId, LocalDate dateArrete);

    /** Export PDF (portrait). */
    byte[] exportPdf(Long exerciceId, LocalDate dateArrete);
}
