package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceComptableDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BalanceFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.BilanDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.CompteResultatDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.GrandLivreFilterDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalEditionDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.JournalFilterDto;
import com.cityprojects.citybackend.service.finance.comptabilite.BalanceComptableService;
import com.cityprojects.citybackend.service.finance.comptabilite.BilanService;
import com.cityprojects.citybackend.service.finance.comptabilite.CompteResultatService;
import com.cityprojects.citybackend.service.finance.comptabilite.GrandLivreService;
import com.cityprojects.citybackend.service.finance.comptabilite.JournalEditionService;
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
 * REST API des etats de synthese OHADA (B5).
 *
 * <p>Matrice des roles : lecture / export reserves a
 * {@code SUPERADMIN, ADMIN, GERANT}. Aucune mutation - tous les endpoints
 * sont en GET. Reponses JSON : DTOs directs sans enveloppe {@code ApiResponse}
 * (coherent avec finance B1-B4). Reponses binaires :
 * {@code ResponseEntity<byte[]>} avec entetes
 * {@code Content-Type} + {@code Content-Disposition: attachment}.</p>
 *
 * <p>Aucun {@code hotelId} dans les payloads / query strings : le tenant est
 * extrait de {@code TenantContext} positionne par {@code JwtAuthenticationFilter}.</p>
 */
@RestController
@RequestMapping("/api/finance/etats")
public class EtatsComptablesController {

    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final BalanceComptableService balanceService;
    private final GrandLivreService grandLivreService;
    private final JournalEditionService journalService;
    private final BilanService bilanService;
    private final CompteResultatService compteResultatService;

    public EtatsComptablesController(BalanceComptableService balanceService,
                                      GrandLivreService grandLivreService,
                                      JournalEditionService journalService,
                                      BilanService bilanService,
                                      CompteResultatService compteResultatService) {
        this.balanceService = balanceService;
        this.grandLivreService = grandLivreService;
        this.journalService = journalService;
        this.bilanService = bilanService;
        this.compteResultatService = compteResultatService;
    }

    // ------------------------------------------------------------------
    // BALANCE
    // ------------------------------------------------------------------
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<BalanceComptableDto> balance(
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(name = "classe", required = false) Integer classe) {
        return ResponseEntity.ok(balanceService.compute(
                new BalanceFilterDto(exerciceId, dateDebut, dateFin, classe)));
    }

    @GetMapping("/balance/export/xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> balanceXlsx(
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(name = "classe", required = false) Integer classe) {
        byte[] body = balanceService.exportXlsx(
                new BalanceFilterDto(exerciceId, dateDebut, dateFin, classe));
        return attachment("balance.xlsx", MediaType.parseMediaType(MIME_XLSX), body);
    }

    @GetMapping("/balance/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> balancePdf(
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(name = "classe", required = false) Integer classe) {
        byte[] body = balanceService.exportPdf(
                new BalanceFilterDto(exerciceId, dateDebut, dateFin, classe));
        return attachment("balance.pdf", MediaType.APPLICATION_PDF, body);
    }

    // ------------------------------------------------------------------
    // GRAND LIVRE
    // ------------------------------------------------------------------
    @GetMapping("/grand-livre")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<GrandLivreDto> grandLivre(
            @RequestParam(name = "compteCode", required = false) String compteCode,
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(grandLivreService.compute(
                new GrandLivreFilterDto(compteCode, exerciceId, dateDebut, dateFin)));
    }

    @GetMapping("/grand-livre/export/xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> grandLivreXlsx(
            @RequestParam(name = "compteCode", required = false) String compteCode,
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = grandLivreService.exportXlsx(
                new GrandLivreFilterDto(compteCode, exerciceId, dateDebut, dateFin));
        return attachment("grand-livre.xlsx", MediaType.parseMediaType(MIME_XLSX), body);
    }

    @GetMapping("/grand-livre/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> grandLivrePdf(
            @RequestParam(name = "compteCode", required = false) String compteCode,
            @RequestParam(name = "exerciceId", required = false) Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = grandLivreService.exportPdf(
                new GrandLivreFilterDto(compteCode, exerciceId, dateDebut, dateFin));
        return attachment("grand-livre.pdf", MediaType.APPLICATION_PDF, body);
    }

    // ------------------------------------------------------------------
    // JOURNAL
    // ------------------------------------------------------------------
    @GetMapping("/journal")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<JournalEditionDto> journal(
            @RequestParam(name = "journalId") Long journalId,
            @RequestParam(name = "dateDebut")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(journalService.compute(
                new JournalFilterDto(journalId, dateDebut, dateFin)));
    }

    @GetMapping("/journal/export/xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> journalXlsx(
            @RequestParam(name = "journalId") Long journalId,
            @RequestParam(name = "dateDebut")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = journalService.exportXlsx(new JournalFilterDto(journalId, dateDebut, dateFin));
        return attachment("journal.xlsx", MediaType.parseMediaType(MIME_XLSX), body);
    }

    @GetMapping("/journal/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> journalPdf(
            @RequestParam(name = "journalId") Long journalId,
            @RequestParam(name = "dateDebut")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = journalService.exportPdf(new JournalFilterDto(journalId, dateDebut, dateFin));
        return attachment("journal.pdf", MediaType.APPLICATION_PDF, body);
    }

    // ------------------------------------------------------------------
    // BILAN
    // ------------------------------------------------------------------
    @GetMapping("/bilan")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<BilanDto> bilan(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateArrete", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateArrete) {
        return ResponseEntity.ok(bilanService.compute(exerciceId, dateArrete));
    }

    @GetMapping("/bilan/export/xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> bilanXlsx(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateArrete", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateArrete) {
        byte[] body = bilanService.exportXlsx(exerciceId, dateArrete);
        return attachment("bilan.xlsx", MediaType.parseMediaType(MIME_XLSX), body);
    }

    @GetMapping("/bilan/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> bilanPdf(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateArrete", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateArrete) {
        byte[] body = bilanService.exportPdf(exerciceId, dateArrete);
        return attachment("bilan.pdf", MediaType.APPLICATION_PDF, body);
    }

    // ------------------------------------------------------------------
    // COMPTE DE RESULTAT
    // ------------------------------------------------------------------
    @GetMapping("/compte-resultat")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<CompteResultatDto> compteResultat(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(compteResultatService.compute(exerciceId, dateDebut, dateFin));
    }

    @GetMapping("/compte-resultat/export/xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> compteResultatXlsx(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = compteResultatService.exportXlsx(exerciceId, dateDebut, dateFin);
        return attachment("compte-resultat.xlsx", MediaType.parseMediaType(MIME_XLSX), body);
    }

    @GetMapping("/compte-resultat/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<byte[]> compteResultatPdf(
            @RequestParam(name = "exerciceId") Long exerciceId,
            @RequestParam(name = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(name = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] body = compteResultatService.exportPdf(exerciceId, dateDebut, dateFin);
        return attachment("compte-resultat.pdf", MediaType.APPLICATION_PDF, body);
    }

    private static ResponseEntity<byte[]> attachment(String filename, MediaType type, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body == null ? 0 : body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
