package com.cityprojects.citybackend.service.reporting.export;

import com.cityprojects.citybackend.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Implementation POI XSSF (Tour 40 MVP). In-memory workbook : suffisant pour les
 * volumes attendus (< 10k lignes). Si besoin de scale, basculer sur {@code SXSSFWorkbook}.
 */
@Service
public class XlsxExportServiceImpl implements XlsxExportService {

    private static final Logger log = LoggerFactory.getLogger(XlsxExportServiceImpl.class);
    private static final String MONEY_FORMAT = "#,##0.00";
    private static final String DECIMAL_FORMAT = "#,##0.00";
    private static final String INTEGER_FORMAT = "#,##0";
    private static final String DATE_FORMAT = "dd/MM/yyyy";
    private static final String DATETIME_FORMAT = "dd/MM/yyyy HH:mm";

    @Override
    public <T> byte[] export(String sheetName, List<ColumnSpec<T>> columns, List<T> rows) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);
            CreationHelper helper = workbook.getCreationHelper();
            DataFormat df = workbook.createDataFormat();

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle moneyStyle = buildDataStyle(workbook, df.getFormat(MONEY_FORMAT));
            CellStyle decimalStyle = buildDataStyle(workbook, df.getFormat(DECIMAL_FORMAT));
            CellStyle integerStyle = buildDataStyle(workbook, df.getFormat(INTEGER_FORMAT));
            CellStyle dateStyle = buildDataStyle(workbook, df.getFormat(DATE_FORMAT));
            CellStyle datetimeStyle = buildDataStyle(workbook, df.getFormat(DATETIME_FORMAT));

            // Header
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
            }

            // Body
            if (rows != null) {
                for (int r = 0; r < rows.size(); r++) {
                    T item = rows.get(r);
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < columns.size(); c++) {
                        ColumnSpec<T> spec = columns.get(c);
                        Cell cell = row.createCell(c);
                        Object raw = item == null ? null : spec.extractor().apply(item);
                        writeCell(cell, raw, spec.type(), moneyStyle, decimalStyle,
                                integerStyle, dateStyle, datetimeStyle);
                    }
                }
            }

            // Auto-size columns (best effort)
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Pas un catch silencieux : on logge + on relance comme erreur metier (cle i18n)
            log.error("XLSX export failed for sheet {}", sheetName, e);
            throw new BusinessException("error.report.export.xlsx.failed");
        }
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle buildDataStyle(XSSFWorkbook workbook, short dataFormat) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat);
        return style;
    }

    private void writeCell(Cell cell, Object raw, ColumnType type,
                           CellStyle money, CellStyle decimal, CellStyle integer,
                           CellStyle date, CellStyle datetime) {
        if (raw == null) {
            cell.setBlank();
            return;
        }
        switch (type) {
            case TEXT -> cell.setCellValue(raw.toString());
            case INTEGER -> {
                if (raw instanceof Number n) {
                    cell.setCellValue(n.longValue());
                    cell.setCellStyle(integer);
                } else {
                    cell.setCellValue(raw.toString());
                }
            }
            case DECIMAL -> {
                if (raw instanceof BigDecimal bd) {
                    cell.setCellValue(bd.doubleValue());
                    cell.setCellStyle(decimal);
                } else if (raw instanceof Number n) {
                    cell.setCellValue(n.doubleValue());
                    cell.setCellStyle(decimal);
                } else {
                    cell.setCellValue(raw.toString());
                }
            }
            case MONEY -> {
                if (raw instanceof BigDecimal bd) {
                    cell.setCellValue(bd.doubleValue());
                    cell.setCellStyle(money);
                } else if (raw instanceof Number n) {
                    cell.setCellValue(n.doubleValue());
                    cell.setCellStyle(money);
                } else {
                    cell.setCellValue(raw.toString());
                }
            }
            case DATE -> {
                if (raw instanceof LocalDate ld) {
                    cell.setCellValue(Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    cell.setCellStyle(date);
                } else {
                    cell.setCellValue(raw.toString());
                }
            }
            case DATETIME -> {
                if (raw instanceof LocalDateTime ldt) {
                    cell.setCellValue(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
                    cell.setCellStyle(datetime);
                } else {
                    cell.setCellValue(raw.toString());
                }
            }
            default -> cell.setCellValue(raw.toString());
        }
    }
}
