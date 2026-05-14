package com.cityprojects.citybackend.service.reporting.export;

import java.util.List;

/**
 * Wrapper Apache POI 5.3.0 pour les exports XLSX (Tour 40 MVP).
 *
 * <p>Convention : 1 ligne d'en-tete bold, alternance de couleur de fond, format
 * de date {@code dd/MM/yyyy}, format monetaire MRU avec 2 decimales.</p>
 */
public interface XlsxExportService {

    /**
     * Construit un classeur XLSX a partir de colonnes typees et de lignes
     * de donnees brutes. Pas de typage de lignes, on s'en remet a
     * {@link ColumnSpec#extractor()} pour aller chercher la valeur.
     *
     * @param sheetName  nom de la feuille
     * @param columns    description des colonnes (titre + type + extracteur)
     * @param rows       liste des objets a serialiser
     * @return contenu binaire .xlsx
     */
    <T> byte[] export(String sheetName, List<ColumnSpec<T>> columns, List<T> rows);

    /**
     * Description d'une colonne d'export. Generique sur le type des lignes.
     *
     * @param <T> type des lignes (DTO)
     */
    record ColumnSpec<T>(String header, ColumnType type, java.util.function.Function<T, Object> extractor) {
    }

    /** Format de cellule (gere le styling cote impl). */
    enum ColumnType {
        TEXT,
        INTEGER,
        DECIMAL,
        DATE,
        DATETIME,
        MONEY
    }
}
