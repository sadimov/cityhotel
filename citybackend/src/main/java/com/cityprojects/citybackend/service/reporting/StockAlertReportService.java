package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.StockAlertDto;

import java.util.List;

/**
 * Rapport R-INV-001 (alertes stock).
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface StockAlertReportService {

    List<StockAlertDto> listStockAlerts();

    byte[] exportXlsx();

    byte[] exportDocx();

    byte[] exportPdf();
}
