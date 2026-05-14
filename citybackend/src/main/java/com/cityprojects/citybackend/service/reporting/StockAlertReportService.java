package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.StockAlertDto;

import java.util.List;

/**
 * Service du rapport R-INV-001 (alertes stock). Tour 40 MVP.
 */
public interface StockAlertReportService {

    /** Etat courant des produits sous seuil d'alerte (un par tenant, pas de periode). */
    List<StockAlertDto> listStockAlerts();

    /** Variante XLSX (binaire). */
    byte[] exportXlsx();
}
