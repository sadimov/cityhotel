package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.CARecapDto;
import com.cityprojects.citybackend.dto.reporting.NightAuditRecapDto;
import com.cityprojects.citybackend.dto.reporting.OccupationDto;
import com.cityprojects.citybackend.dto.reporting.ReportPeriode;
import com.cityprojects.citybackend.dto.reporting.StockAlertDto;
import com.cityprojects.citybackend.dto.reporting.TopClientDto;
import com.cityprojects.citybackend.service.reporting.CARecapReportService;
import com.cityprojects.citybackend.service.reporting.NightAuditReportService;
import com.cityprojects.citybackend.service.reporting.OccupationReportService;
import com.cityprojects.citybackend.service.reporting.StockAlertReportService;
import com.cityprojects.citybackend.service.reporting.TopClientsReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API du module reporting (Tour 40 MVP, 5 rapports P0).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/reports/occupation} R-HEB-001 (JSON)</li>
 *   <li>{@code GET /api/reports/occupation/export.pdf} R-HEB-001 (PDF)</li>
 *   <li>{@code GET /api/reports/ca} R-FIN-001 (JSON)</li>
 *   <li>{@code GET /api/reports/ca/export.xlsx} R-FIN-001 (XLSX)</li>
 *   <li>{@code GET /api/reports/stock-alerts} R-INV-001 (JSON)</li>
 *   <li>{@code GET /api/reports/stock-alerts/export.xlsx} R-INV-001 (XLSX)</li>
 *   <li>{@code GET /api/reports/night-audit} R-NA-001 (JSON)</li>
 *   <li>{@code GET /api/reports/night-audit/export.pdf} R-NA-001 (PDF)</li>
 *   <li>{@code GET /api/reports/top-clients} R-CLI-001 (JSON)</li>
 *   <li>{@code GET /api/reports/top-clients/export.xlsx} R-CLI-001 (XLSX)</li>
 * </ul>
 *
 * <p>Aucun {@code hotelId} dans les payloads / query strings — le tenant est
 * extrait de {@code TenantContext} (positionne par {@code JwtAuthenticationFilter}).</p>
 *
 * <p>Securite : {@code @PreAuthorize} par endpoint selon la matrice
 * {@code REPORTING_SCOPE.md} §5.</p>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final OccupationReportService occupationService;
    private final CARecapReportService caRecapService;
    private final StockAlertReportService stockAlertService;
    private final NightAuditReportService nightAuditService;
    private final TopClientsReportService topClientsService;

    public ReportController(OccupationReportService occupationService,
                            CARecapReportService caRecapService,
                            StockAlertReportService stockAlertService,
                            NightAuditReportService nightAuditService,
                            TopClientsReportService topClientsService) {
        this.occupationService = occupationService;
        this.caRecapService = caRecapService;
        this.stockAlertService = stockAlertService;
        this.nightAuditService = nightAuditService;
        this.topClientsService = topClientsService;
    }

    // ------------------------------------------------------------------
    // R-HEB-001 Occupation
    // ------------------------------------------------------------------
    @GetMapping("/occupation")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<OccupationDto> getOccupation(
            @RequestParam(name = "periode", defaultValue = "JOUR") ReportPeriode periode,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(occupationService.computeOccupation(periode, from, to, LocalDate.now()));
    }

    @GetMapping(value = "/occupation/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<byte[]> exportOccupationPdf(
            @RequestParam(name = "periode", defaultValue = "JOUR") ReportPeriode periode,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = occupationService.exportPdf(periode, from, to, LocalDate.now());
        return attachment("occupation.pdf", MediaType.APPLICATION_PDF, pdf);
    }

    // ------------------------------------------------------------------
    // R-FIN-001 CA recap
    // ------------------------------------------------------------------
    @GetMapping("/ca")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<CARecapDto> getCA(
            @RequestParam(name = "periode", defaultValue = "SEMAINE") ReportPeriode periode,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(caRecapService.computeCA(periode, from, to, LocalDate.now()));
    }

    @GetMapping(value = "/ca/export.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> exportCaXlsx(
            @RequestParam(name = "periode", defaultValue = "SEMAINE") ReportPeriode periode,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] xlsx = caRecapService.exportXlsx(periode, from, to, LocalDate.now());
        return attachment("ca-recap.xlsx", xlsxMediaType(), xlsx);
    }

    // ------------------------------------------------------------------
    // R-INV-001 Alertes stock
    // ------------------------------------------------------------------
    @GetMapping("/stock-alerts")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<List<StockAlertDto>> getStockAlerts() {
        return ResponseEntity.ok(stockAlertService.listStockAlerts());
    }

    @GetMapping(value = "/stock-alerts/export.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<byte[]> exportStockAlertsXlsx() {
        byte[] xlsx = stockAlertService.exportXlsx();
        return attachment("alertes-stock.xlsx", xlsxMediaType(), xlsx);
    }

    // ------------------------------------------------------------------
    // R-NA-001 Night audit recap
    // ------------------------------------------------------------------
    @GetMapping("/night-audit")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','NIGHTAUDIT')")
    public ResponseEntity<List<NightAuditRecapDto>> getNightAuditRecap(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(nightAuditService.computeRecap(from, to));
    }

    @GetMapping(value = "/night-audit/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','NIGHTAUDIT')")
    public ResponseEntity<byte[]> exportNightAuditPdf(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = nightAuditService.exportPdf(from, to);
        return attachment("night-audit.pdf", MediaType.APPLICATION_PDF, pdf);
    }

    // ------------------------------------------------------------------
    // R-CLI-001 Top clients
    // ------------------------------------------------------------------
    @GetMapping("/top-clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<List<TopClientDto>> getTopClients(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(topClientsService.findTopClients(from, to, limit));
    }

    @GetMapping(value = "/top-clients/export.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<byte[]> exportTopClientsXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        byte[] xlsx = topClientsService.exportXlsx(from, to, limit);
        return attachment("top-clients.xlsx", xlsxMediaType(), xlsx);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static MediaType xlsxMediaType() {
        return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private static ResponseEntity<byte[]> attachment(String filename, MediaType type, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body == null ? 0 : body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
