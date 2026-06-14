package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;

import java.time.LocalDate;

/**
 * Rapport R-INV-002 — Mouvements valorisés.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface MouvementsValorisesReportService {

    MouvementValoriseDto computeMouvements(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);

    byte[] exportXlsx(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);

    byte[] exportDocx(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);

    byte[] exportPdf(LocalDate from, LocalDate to, TypeMouvementStock typeFilter);
}
