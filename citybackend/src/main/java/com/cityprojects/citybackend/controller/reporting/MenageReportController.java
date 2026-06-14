package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.ChargePersonnelDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto;
import com.cityprojects.citybackend.dto.reporting.RecapTacheDto.TacheGroupBy;
import com.cityprojects.citybackend.service.reporting.ChargePersonnelReportService;
import com.cityprojects.citybackend.service.reporting.RecapTachesReportService;
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
 * REST API menage reporting P2 (Tour 41) :
 * <ul>
 *   <li>R-MEN-001 Recap taches</li>
 *   <li>R-MEN-002 Charge personnel</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/menage")
public class MenageReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String ROLES_MEN = "hasAnyRole('SUPERADMIN','ADMIN','GERANT','MENAGE')";

    private final RecapTachesReportService recapService;
    private final ChargePersonnelReportService chargeService;

    public MenageReportController(RecapTachesReportService recapService,
                                  ChargePersonnelReportService chargeService) {
        this.recapService = recapService;
        this.chargeService = chargeService;
    }

    @GetMapping("/recap-taches")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<RecapTacheDto> getRecapTaches(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") TacheGroupBy groupBy) {
        return ResponseEntity.ok(recapService.computeRecap(from, to, groupBy));
    }

    @GetMapping(value = "/recap-taches/export.xlsx")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportRecapTachesXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") TacheGroupBy groupBy) {
        return attachment("recap-taches.xlsx", xlsxMediaType(),
                recapService.exportXlsx(from, to, groupBy));
    }

    @GetMapping(value = "/recap-taches/export.docx")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportRecapTachesDocx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") TacheGroupBy groupBy) {
        return attachment("recap-taches.docx", docxMediaType(),
                recapService.exportDocx(from, to, groupBy));
    }

    @GetMapping(value = "/recap-taches/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportRecapTachesPdf(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "groupBy", defaultValue = "JOUR") TacheGroupBy groupBy) {
        return attachment("recap-taches.pdf", MediaType.APPLICATION_PDF,
                recapService.exportPdf(from, to, groupBy));
    }

    @GetMapping("/charge-personnel")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<ChargePersonnelDto> getChargePersonnel(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(chargeService.computeCharge(from, to));
    }

    @GetMapping(value = "/charge-personnel/export.xlsx")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportChargePersonnelXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("charge-personnel.xlsx", xlsxMediaType(),
                chargeService.exportXlsx(from, to));
    }

    @GetMapping(value = "/charge-personnel/export.docx")
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportChargePersonnelDocx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("charge-personnel.docx", docxMediaType(),
                chargeService.exportDocx(from, to));
    }

    @GetMapping(value = "/charge-personnel/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_MEN)
    public ResponseEntity<byte[]> exportChargePersonnelPdf(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("charge-personnel.pdf", MediaType.APPLICATION_PDF,
                chargeService.exportPdf(from, to));
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
