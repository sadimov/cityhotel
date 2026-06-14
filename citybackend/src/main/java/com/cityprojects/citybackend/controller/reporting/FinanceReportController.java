package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.EncoursClientDto;
import com.cityprojects.citybackend.dto.reporting.TopSocieteDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto;
import com.cityprojects.citybackend.dto.reporting.TvaRecapDto.TvaGroupBy;
import com.cityprojects.citybackend.service.reporting.EncoursClientsReportService;
import com.cityprojects.citybackend.service.reporting.TopSocietesReportService;
import com.cityprojects.citybackend.service.reporting.TvaCollecteeReportService;
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
import java.util.List;

/**
 * REST API finance reporting P1/P2 (Tour 41) :
 * <ul>
 *   <li>R-FIN-002 Encours clients</li>
 *   <li>R-FIN-003 TVA collectee</li>
 *   <li>R-FIN-004 Top societes B2B</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/finance")
public class FinanceReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    // NIGHTAUDIT inclus : l'auditeur de nuit consulte les encours / TVA / top
    // sociétés dans le cadre de la clôture quotidienne (lecture seule).
    private static final String ROLES_FIN = "hasAnyRole('SUPERADMIN','ADMIN','GERANT','NIGHTAUDIT')";

    private final EncoursClientsReportService encoursService;
    private final TvaCollecteeReportService tvaService;
    private final TopSocietesReportService topSocietesService;

    public FinanceReportController(EncoursClientsReportService encoursService,
                                   TvaCollecteeReportService tvaService,
                                   TopSocietesReportService topSocietesService) {
        this.encoursService = encoursService;
        this.tvaService = tvaService;
        this.topSocietesService = topSocietesService;
    }

    @GetMapping("/encours-clients")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<EncoursClientDto> getEncours(
            @RequestParam(name = "reference", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference) {
        return ResponseEntity.ok(encoursService.computeEncours(reference));
    }

    @GetMapping(value = "/encours-clients/export.xlsx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportEncoursXlsx(
            @RequestParam(name = "reference", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference) {
        return attachment("encours-clients.xlsx", xlsxMediaType(),
                encoursService.exportXlsx(reference));
    }

    @GetMapping(value = "/encours-clients/export.docx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportEncoursDocx(
            @RequestParam(name = "reference", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference) {
        return attachment("encours-clients.docx", docxMediaType(),
                encoursService.exportDocx(reference));
    }

    @GetMapping(value = "/encours-clients/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportEncoursPdf(
            @RequestParam(name = "reference", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference) {
        return attachment("encours-clients.pdf", MediaType.APPLICATION_PDF,
                encoursService.exportPdf(reference));
    }

    @GetMapping("/tva-recap")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<TvaRecapDto> getTvaRecap(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "MOIS") TvaGroupBy groupBy) {
        return ResponseEntity.ok(tvaService.computeTvaRecap(from, to, groupBy));
    }

    @GetMapping(value = "/tva-recap/export.xlsx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTvaRecapXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "MOIS") TvaGroupBy groupBy) {
        return attachment("tva-recap.xlsx", xlsxMediaType(),
                tvaService.exportXlsx(from, to, groupBy));
    }

    @GetMapping(value = "/tva-recap/export.docx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTvaRecapDocx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "MOIS") TvaGroupBy groupBy) {
        return attachment("tva-recap.docx", docxMediaType(),
                tvaService.exportDocx(from, to, groupBy));
    }

    @GetMapping(value = "/tva-recap/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTvaRecapPdf(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "MOIS") TvaGroupBy groupBy) {
        return attachment("tva-recap.pdf", MediaType.APPLICATION_PDF,
                tvaService.exportPdf(from, to, groupBy));
    }

    @GetMapping("/top-societes")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<List<TopSocieteDto>> getTopSocietes(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(topSocietesService.findTopSocietes(from, to, limit));
    }

    @GetMapping(value = "/top-societes/export.xlsx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTopSocietesXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return attachment("top-societes.xlsx", xlsxMediaType(),
                topSocietesService.exportXlsx(from, to, limit));
    }

    @GetMapping(value = "/top-societes/export.docx")
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTopSocietesDocx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return attachment("top-societes.docx", docxMediaType(),
                topSocietesService.exportDocx(from, to, limit));
    }

    @GetMapping(value = "/top-societes/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_FIN)
    public ResponseEntity<byte[]> exportTopSocietesPdf(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return attachment("top-societes.pdf", MediaType.APPLICATION_PDF,
                topSocietesService.exportPdf(from, to, limit));
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
