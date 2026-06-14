package com.cityprojects.citybackend.service.reporting.export;

/**
 * Service générique d'export tabulaire 3 formats (Phase A Tour 51ter).
 *
 * <p>Produit Excel (POI XSSF), Word (POI XWPF) et PDF (OpenPDF) à partir
 * d'un même {@link ReportDocument}. Bordures simples sur toutes les
 * cellules de table — consigne user 2026-06-13.</p>
 *
 * <p>Cible : les exports "simples tableau + KPIs" du module reporting.
 * Les rapports complexes (occupation jrxml, kpi-reception jrxml) restent
 * gérés par {@link PdfExportService} pour conserver leur mise en page.</p>
 */
public interface DocumentExportService {

    /** Excel (.xlsx) — workbook XSSF avec bordures partout. */
    byte[] toXlsx(ReportDocument doc);

    /** Word (.docx) — document XWPF avec bordures sur le tableau. */
    byte[] toDocx(ReportDocument doc);

    /** PDF (.pdf) — OpenPDF avec PdfPTable bordurée. */
    byte[] toPdf(ReportDocument doc);
}
