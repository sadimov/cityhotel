package com.cityprojects.citybackend.service.reporting;

import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport R-INV-003 — Bons de commande pendants + rotation produits.
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés via
 * {@code DocumentExportService} avec bordures partout.</p>
 */
public interface BcPendantsRotationReportService {

    List<BcPendantDto> findBcPendants();

    List<RotationProduitDto> computeRotation(LocalDate from, LocalDate to);

    byte[] exportBcPendantsXlsx();
    byte[] exportBcPendantsDocx();
    byte[] exportBcPendantsPdf();

    byte[] exportRotationXlsx(LocalDate from, LocalDate to);
    byte[] exportRotationDocx(LocalDate from, LocalDate to);
    byte[] exportRotationPdf(LocalDate from, LocalDate to);
}
