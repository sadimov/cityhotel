package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.JournalCaisseDto;
import com.cityprojects.citybackend.dto.reporting.TicketMarginDto;
import com.cityprojects.citybackend.dto.reporting.TopArticleDto;
import com.cityprojects.citybackend.service.reporting.JournalCaisseReportService;
import com.cityprojects.citybackend.service.reporting.TicketMarginReportService;
import com.cityprojects.citybackend.service.reporting.TopArticlesReportService;
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
 * REST API restaurant reporting P2 (Tour 41) :
 * <ul>
 *   <li>R-RES-001 Journal de caisse</li>
 *   <li>R-RES-002 Top articles</li>
 *   <li>R-RES-003 Ticket moyen + marge</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/restaurant")
public class RestaurantReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ROLES_RES = "hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')";

    private final JournalCaisseReportService journalCaisseService;
    private final TopArticlesReportService topArticlesService;
    private final TicketMarginReportService ticketMarginService;

    public RestaurantReportController(JournalCaisseReportService journalCaisseService,
                                      TopArticlesReportService topArticlesService,
                                      TicketMarginReportService ticketMarginService) {
        this.journalCaisseService = journalCaisseService;
        this.topArticlesService = topArticlesService;
        this.ticketMarginService = ticketMarginService;
    }

    @GetMapping("/journal-caisse")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<JournalCaisseDto> getJournal(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(journalCaisseService.computeJournal(date));
    }

    @GetMapping(value = "/journal-caisse/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<byte[]> exportJournalPdf(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("journal-caisse.pdf", MediaType.APPLICATION_PDF,
                journalCaisseService.exportPdf(date));
    }

    @GetMapping(value = "/journal-caisse/export.xlsx")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<byte[]> exportJournalXlsx(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attachment("journal-caisse.xlsx", xlsxMediaType(),
                journalCaisseService.exportXlsx(date));
    }

    @GetMapping("/top-articles")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<TopArticleDto> getTopArticles(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(topArticlesService.findTopArticles(from, to, limit));
    }

    @GetMapping(value = "/top-articles/export.xlsx")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<byte[]> exportTopArticlesXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return attachment("top-articles.xlsx", xlsxMediaType(),
                topArticlesService.exportXlsx(from, to, limit));
    }

    @GetMapping("/ticket-moyen")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<TicketMarginDto> getTicketMoyen(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ticketMarginService.computeMargin(from, to));
    }

    @GetMapping(value = "/ticket-moyen/export.xlsx")
    @PreAuthorize(ROLES_RES)
    public ResponseEntity<byte[]> exportTicketMoyenXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("ticket-moyen.xlsx", xlsxMediaType(),
                ticketMarginService.exportXlsx(from, to));
    }

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
