package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.DashboardDirectionDto;
import com.cityprojects.citybackend.service.reporting.DashboardDirectionReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API direction reporting :
 * <ul>
 *   <li>R-DIR-001 Dashboard direction agrégé (occupation, CA, alertes, tâches, KPIs).</li>
 * </ul>
 *
 * <p>Tour 51ter : exports XLSX / DOCX / PDF unifiés avec bordures.</p>
 */
@RestController
@RequestMapping("/api/reports/direction")
public class DirectionReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String ROLES_DIR = "hasAnyRole('SUPERADMIN','ADMIN','GERANT')";

    private final DashboardDirectionReportService dashboardService;

    public DirectionReportController(DashboardDirectionReportService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(ROLES_DIR)
    public ResponseEntity<DashboardDirectionDto> getDashboard(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dashboardService.computeDashboard(date));
    }

    @GetMapping(value = "/dashboard/export.xlsx")
    @PreAuthorize(ROLES_DIR)
    public ResponseEntity<byte[]> exportDashboardXlsx(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("dashboard-direction.xlsx", xlsxMediaType(),
                dashboardService.exportXlsx(date));
    }

    @GetMapping(value = "/dashboard/export.docx")
    @PreAuthorize(ROLES_DIR)
    public ResponseEntity<byte[]> exportDashboardDocx(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("dashboard-direction.docx", docxMediaType(),
                dashboardService.exportDocx(date));
    }

    @GetMapping(value = "/dashboard/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_DIR)
    public ResponseEntity<byte[]> exportDashboardPdf(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("dashboard-direction.pdf", MediaType.APPLICATION_PDF,
                dashboardService.exportPdf(date));
    }

    private static MediaType xlsxMediaType() {
        return MediaType.parseMediaType(XLSX_MEDIA_TYPE);
    }

    private static MediaType docxMediaType() {
        return MediaType.parseMediaType(DOCX_MEDIA_TYPE);
    }

    private static ResponseEntity<byte[]> attachment(String filename, MediaType type, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body == null ? 0 : body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
