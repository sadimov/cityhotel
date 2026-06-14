package com.cityprojects.citybackend.service.reporting.export;

import com.cityprojects.citybackend.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Implémentation des 3 formats. Bordures partout, header en gris, alternance
 * légère pour la lisibilité (sans casser le contraste impression).
 */
@Service
public class DocumentExportServiceImpl implements DocumentExportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExportServiceImpl.class);

    // ──────────────────────────────────────────────────────────────────────
    //  XLSX
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public byte[] toXlsx(ReportDocument doc) {
        if (doc == null) throw new IllegalArgumentException("doc must not be null");
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(safeSheetName(doc.title()));

            // Styles
            CellStyle titleStyle = wb.createCellStyle();
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle subStyle = wb.createCellStyle();
            XSSFFont subFont = wb.createFont();
            subFont.setItalic(true);
            subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subStyle.setFont(subFont);

            CellStyle kpiLabelStyle = boxed(wb, true, IndexedColors.GREY_25_PERCENT);
            CellStyle kpiValueStyle = boxed(wb, false, null);
            CellStyle headerStyle = boxed(wb, true, IndexedColors.GREY_40_PERCENT);
            CellStyle bodyStyle = boxed(wb, false, null);

            int r = 0;
            // Titre + période
            Row titleRow = sheet.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(doc.title() != null ? doc.title() : "");
            titleCell.setCellStyle(titleStyle);
            if (doc.periodLabel() != null && !doc.periodLabel().isBlank()) {
                Row subRow = sheet.createRow(r++);
                Cell subCell = subRow.createCell(0);
                subCell.setCellValue(doc.periodLabel());
                subCell.setCellStyle(subStyle);
            }
            r++; // ligne vide

            // KPIs (2 colonnes : label | value)
            if (doc.kpis() != null && !doc.kpis().isEmpty()) {
                for (ReportDocument.Kpi kpi : doc.kpis()) {
                    Row kpiRow = sheet.createRow(r++);
                    Cell labelCell = kpiRow.createCell(0);
                    labelCell.setCellValue(kpi.label() != null ? kpi.label() : "");
                    labelCell.setCellStyle(kpiLabelStyle);
                    Cell valueCell = kpiRow.createCell(1);
                    valueCell.setCellValue(kpi.value() != null ? kpi.value() : "");
                    valueCell.setCellStyle(kpiValueStyle);
                }
                r++; // ligne vide
            }

            // Table : header
            ReportDocument.Table table = doc.table();
            if (table != null) {
                if (table.title() != null && !table.title().isBlank()) {
                    Row tableTitleRow = sheet.createRow(r++);
                    Cell tcell = tableTitleRow.createCell(0);
                    tcell.setCellValue(table.title());
                    tcell.setCellStyle(titleStyle);
                }
                List<String> headers = table.headers() != null ? table.headers() : List.of();
                if (!headers.isEmpty()) {
                    Row headerRow = sheet.createRow(r++);
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = headerRow.createCell(c);
                        cell.setCellValue(headers.get(c));
                        cell.setCellStyle(headerStyle);
                    }
                }
                List<List<Object>> rows = table.rows() != null ? table.rows() : List.of();
                for (List<Object> rowValues : rows) {
                    Row dataRow = sheet.createRow(r++);
                    for (int c = 0; c < rowValues.size(); c++) {
                        Cell cell = dataRow.createCell(c);
                        Object v = rowValues.get(c);
                        writeXlsxValue(cell, v);
                        cell.setCellStyle(bodyStyle);
                    }
                }
                int nbCols = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
                for (int c = 0; c < Math.max(2, nbCols); c++) {
                    sheet.autoSizeColumn(c);
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("XLSX export failed", e);
            throw new BusinessException("error.report.export.xlsx.failed");
        }
    }

    private static void writeXlsxValue(Cell cell, Object v) {
        if (v == null) { cell.setBlank(); return; }
        if (v instanceof BigDecimal bd) { cell.setCellValue(bd.doubleValue()); return; }
        if (v instanceof Number n) { cell.setCellValue(n.doubleValue()); return; }
        cell.setCellValue(v.toString());
    }

    /** Style bordurée tout autour, optionnel bold + fill. */
    private static CellStyle boxed(XSSFWorkbook wb, boolean bold, IndexedColors fill) {
        CellStyle s = wb.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        if (bold) {
            XSSFFont f = wb.createFont();
            f.setBold(true);
            s.setFont(f);
            s.setAlignment(HorizontalAlignment.CENTER);
        }
        if (fill != null) {
            s.setFillForegroundColor(fill.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    private static String safeSheetName(String raw) {
        if (raw == null || raw.isBlank()) return "Rapport";
        String cleaned = raw.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  DOCX
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public byte[] toDocx(ReportDocument doc) {
        if (doc == null) throw new IllegalArgumentException("doc must not be null");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Titre principal
            XWPFParagraph titleP = document.createParagraph();
            titleP.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun titleRun = titleP.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(doc.title() != null ? doc.title() : "");

            // Sous-titre / période
            if (doc.periodLabel() != null && !doc.periodLabel().isBlank()) {
                XWPFParagraph subP = document.createParagraph();
                XWPFRun subRun = subP.createRun();
                subRun.setItalic(true);
                subRun.setFontSize(10);
                subRun.setColor("666666");
                subRun.setText(doc.periodLabel());
            }

            // KPIs sous forme de table 2 colonnes (label | value), bordurée
            if (doc.kpis() != null && !doc.kpis().isEmpty()) {
                addSpacer(document);
                XWPFTable kpiTable = document.createTable(doc.kpis().size(), 2);
                applyTableBorders(kpiTable);
                for (int i = 0; i < doc.kpis().size(); i++) {
                    ReportDocument.Kpi k = doc.kpis().get(i);
                    XWPFTableRow row = kpiTable.getRow(i);
                    setCellText(row.getCell(0), nullToEmpty(k.label()), true);
                    setCellText(row.getCell(1), nullToEmpty(k.value()), false);
                }
            }

            // Table principale
            ReportDocument.Table table = doc.table();
            if (table != null) {
                addSpacer(document);
                if (table.title() != null && !table.title().isBlank()) {
                    XWPFParagraph tp = document.createParagraph();
                    XWPFRun tr = tp.createRun();
                    tr.setBold(true);
                    tr.setFontSize(12);
                    tr.setText(table.title());
                }
                List<String> headers = table.headers() != null ? table.headers() : List.of();
                List<List<Object>> rows = table.rows() != null ? table.rows() : List.of();
                int nbCols = Math.max(headers.size(),
                        rows.stream().mapToInt(List::size).max().orElse(0));
                if (nbCols > 0) {
                    int nbRows = 1 + rows.size();
                    XWPFTable mainTable = document.createTable(nbRows, nbCols);
                    applyTableBorders(mainTable);
                    XWPFTableRow headerRow = mainTable.getRow(0);
                    for (int c = 0; c < nbCols; c++) {
                        String h = c < headers.size() ? headers.get(c) : "";
                        setCellText(headerRow.getCell(c), h, true);
                    }
                    for (int i = 0; i < rows.size(); i++) {
                        XWPFTableRow row = mainTable.getRow(i + 1);
                        List<Object> values = rows.get(i);
                        for (int c = 0; c < nbCols; c++) {
                            Object v = c < values.size() ? values.get(c) : "";
                            setCellText(row.getCell(c), v == null ? "" : v.toString(), false);
                        }
                    }
                }
            }

            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("DOCX export failed", e);
            throw new BusinessException("error.report.export.docx.failed");
        }
    }

    private static void addSpacer(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        // Supprime le paragraphe par défaut puis remplace
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(10);
        run.setText(text != null ? text : "");
    }

    /** Bordures simples sur les 4 côtés + intérieures. */
    private static void applyTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr() != null
                ? table.getCTTbl().getTblPr()
                : table.getCTTbl().addNewTblPr();
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private static void setBorder(CTBorder b) {
        b.setVal(STBorder.SINGLE);
        b.setSz(java.math.BigInteger.valueOf(4));
        b.setColor("888888");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  PDF (OpenPDF)
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public byte[] toPdf(ReportDocument doc) {
        if (doc == null) throw new IllegalArgumentException("doc must not be null");
        Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(pdf, out);
            pdf.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);
            Font kpiLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font kpiValueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Color headerBg = new Color(80, 80, 80);
            Color kpiLabelBg = new Color(230, 230, 230);

            // Titre
            Paragraph titlePara = new Paragraph(doc.title() != null ? doc.title() : "", titleFont);
            titlePara.setSpacingAfter(4);
            pdf.add(titlePara);

            // Période
            if (doc.periodLabel() != null && !doc.periodLabel().isBlank()) {
                Paragraph sub = new Paragraph(doc.periodLabel(), subFont);
                sub.setSpacingAfter(12);
                pdf.add(sub);
            } else {
                pdf.add(new Paragraph(" "));
            }

            // KPIs
            if (doc.kpis() != null && !doc.kpis().isEmpty()) {
                PdfPTable kpiTable = new PdfPTable(2);
                kpiTable.setWidthPercentage(100);
                kpiTable.setSpacingAfter(12);
                for (ReportDocument.Kpi k : doc.kpis()) {
                    PdfPCell labelCell = new PdfPCell(new Phrase(nullToEmpty(k.label()), kpiLabelFont));
                    labelCell.setBackgroundColor(kpiLabelBg);
                    labelCell.setPadding(6);
                    PdfPCell valueCell = new PdfPCell(new Phrase(nullToEmpty(k.value()), kpiValueFont));
                    valueCell.setPadding(6);
                    kpiTable.addCell(labelCell);
                    kpiTable.addCell(valueCell);
                }
                pdf.add(kpiTable);
            }

            // Table principale
            ReportDocument.Table table = doc.table();
            if (table != null) {
                if (table.title() != null && !table.title().isBlank()) {
                    Paragraph tp = new Paragraph(table.title(),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
                    tp.setSpacingAfter(6);
                    pdf.add(tp);
                }
                List<String> headers = table.headers() != null ? table.headers() : List.of();
                List<List<Object>> rows = table.rows() != null ? table.rows() : List.of();
                int nbCols = Math.max(headers.size(),
                        rows.stream().mapToInt(List::size).max().orElse(0));
                if (nbCols > 0) {
                    PdfPTable pdfTable = new PdfPTable(nbCols);
                    pdfTable.setWidthPercentage(100);

                    // Headers
                    for (int c = 0; c < nbCols; c++) {
                        String h = c < headers.size() ? headers.get(c) : "";
                        PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
                        hc.setBackgroundColor(headerBg);
                        hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                        hc.setPadding(6);
                        pdfTable.addCell(hc);
                    }
                    // Rows — PdfPCell a une bordure de 0.5pt par défaut
                    for (List<Object> values : rows) {
                        for (int c = 0; c < nbCols; c++) {
                            Object v = c < values.size() ? values.get(c) : "";
                            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v.toString(), bodyFont));
                            cell.setPadding(5);
                            pdfTable.addCell(cell);
                        }
                    }
                    pdf.add(pdfTable);
                }
            }

            pdf.close();
            return out.toByteArray();
        } catch (com.lowagie.text.DocumentException | IOException e) {
            log.error("PDF export failed", e);
            try { pdf.close(); } catch (Exception ignored) { /* swallow */ }
            throw new BusinessException("error.report.export.pdf.failed");
        }
    }
}
