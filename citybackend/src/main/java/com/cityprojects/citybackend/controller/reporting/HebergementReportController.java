package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.AlosDto;
import com.cityprojects.citybackend.dto.reporting.AlosDto.AlosGroupBy;
import com.cityprojects.citybackend.dto.reporting.KpiReceptionDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto;
import com.cityprojects.citybackend.dto.reporting.NoShowRateDto.NoShowGroupBy;
import com.cityprojects.citybackend.dto.reporting.ReservationSourceDto;
import com.cityprojects.citybackend.service.reporting.AlosReportService;
import com.cityprojects.citybackend.service.reporting.KpiReceptionReportService;
import com.cityprojects.citybackend.service.reporting.NoShowRateReportService;
import com.cityprojects.citybackend.service.reporting.ReservationSourceReportService;
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
 * REST API hebergement reporting P1 (Tour 41) :
 * <ul>
 *   <li>R-HEB-002 ALOS (Average Length of Stay)</li>
 *   <li>R-HEB-003 Taux de no-show</li>
 *   <li>R-HEB-004 Repartition par source / canal</li>
 *   <li>R-HEB-005 KPIs reception jour</li>
 * </ul>
 *
 * <p>Aucun {@code hotelId} dans les payloads — extrait de {@code TenantContext}.</p>
 */
@RestController
@RequestMapping("/api/reports/hebergement")
public class HebergementReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ROLES_HEB =
            "hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')";

    private final AlosReportService alosService;
    private final NoShowRateReportService noShowService;
    private final ReservationSourceReportService sourceService;
    private final KpiReceptionReportService kpiService;

    public HebergementReportController(AlosReportService alosService,
                                       NoShowRateReportService noShowService,
                                       ReservationSourceReportService sourceService,
                                       KpiReceptionReportService kpiService) {
        this.alosService = alosService;
        this.noShowService = noShowService;
        this.sourceService = sourceService;
        this.kpiService = kpiService;
    }

    // ------------------------------------------------------------------
    // R-HEB-002 ALOS
    // ------------------------------------------------------------------

    @GetMapping("/alos")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<AlosDto> getAlos(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "TYPE_CHAMBRE") AlosGroupBy groupBy) {
        return ResponseEntity.ok(alosService.computeAlos(from, to, groupBy));
    }

    @GetMapping(value = "/alos/export.xlsx")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<byte[]> exportAlosXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "TYPE_CHAMBRE") AlosGroupBy groupBy) {
        return attachment("alos.xlsx", xlsxMediaType(),
                alosService.exportXlsx(from, to, groupBy));
    }

    // ------------------------------------------------------------------
    // R-HEB-003 No-show
    // ------------------------------------------------------------------

    @GetMapping("/no-show-rate")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<NoShowRateDto> getNoShowRate(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") NoShowGroupBy groupBy) {
        return ResponseEntity.ok(noShowService.computeNoShowRate(from, to, groupBy));
    }

    @GetMapping(value = "/no-show-rate/export.xlsx")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<byte[]> exportNoShowRateXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") NoShowGroupBy groupBy) {
        return attachment("no-show-rate.xlsx", xlsxMediaType(),
                noShowService.exportXlsx(from, to, groupBy));
    }

    // ------------------------------------------------------------------
    // R-HEB-004 Sources
    // ------------------------------------------------------------------

    @GetMapping("/sources")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<ReservationSourceDto> getSources(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(sourceService.computeBySource(from, to));
    }

    @GetMapping(value = "/sources/export.xlsx")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<byte[]> exportSourcesXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("sources-reservations.xlsx", xlsxMediaType(),
                sourceService.exportXlsx(from, to));
    }

    // ------------------------------------------------------------------
    // R-HEB-005 KPIs reception
    // ------------------------------------------------------------------

    @GetMapping("/kpi-reception")
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<KpiReceptionDto> getKpiReception(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(kpiService.computeKpis(date));
    }

    @GetMapping(value = "/kpi-reception/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_HEB)
    public ResponseEntity<byte[]> exportKpiReceptionPdf(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("kpi-reception.pdf", MediaType.APPLICATION_PDF,
                kpiService.exportPdf(date));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MediaType xlsxMediaType() {
        return MediaType.parseMediaType(XLSX_MEDIA_TYPE);
    }

    private static ResponseEntity<byte[]> attachment(String filename, MediaType type, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body == null ? 0 : body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
