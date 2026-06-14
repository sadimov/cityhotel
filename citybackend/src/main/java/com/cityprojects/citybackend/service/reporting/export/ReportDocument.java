package com.cityprojects.citybackend.service.reporting.export;

import java.util.List;

/**
 * Structure pivot pour les exports tabulaires Excel / Word / PDF.
 *
 * <p>Convention :</p>
 * <ul>
 *   <li>{@link #title} : titre principal du document (gros, en haut)</li>
 *   <li>{@link #periodLabel} : sous-titre (période, date, contexte). Facultatif.</li>
 *   <li>{@link #kpis} : indicateurs scalaires affichés en bloc compact avant la table.</li>
 *   <li>{@link #table} : tableau principal (header + rows). Toutes les cellules
 *       sont rendues avec une bordure simple par défaut (consigne user 2026-06-13).</li>
 * </ul>
 *
 * <p>Convention rows : chaque {@code List<Object>} représente une ligne. Les
 * cellules acceptent {@code String, Number, BigDecimal} — le format est
 * délégué à l'implémentation d'export ({@code DocumentExportService}).</p>
 */
public record ReportDocument(
        String title,
        String periodLabel,
        List<Kpi> kpis,
        Table table
) {

    /** Indicateur scalaire (libellé + valeur formatée en chaîne). */
    public record Kpi(String label, String value) {
    }

    /** Table principale avec en-tête et lignes. */
    public record Table(String title, List<String> headers, List<List<Object>> rows) {
    }
}
