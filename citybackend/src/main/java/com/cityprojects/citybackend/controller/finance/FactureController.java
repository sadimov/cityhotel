package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureRecapDto;
import com.cityprojects.citybackend.dto.finance.LigneServiceCreateRequest;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementGlobalRequest;
import com.cityprojects.citybackend.dto.finance.PaiementLignesRequest;
import com.cityprojects.citybackend.dto.finance.TransfertLignesRequest;
import com.cityprojects.citybackend.service.finance.FacturePdfService;
import com.cityprojects.citybackend.service.finance.FactureService;
import com.cityprojects.citybackend.service.finance.PaiementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * REST API des factures.
 *
 * <p>Matrice rôles (alignée prompt Tour 19) :
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</li>
 *   <li>Creation : SUPERADMIN, ADMIN, GERANT, RECEPTION.</li>
 *   <li>Validation/Emission : SUPERADMIN, ADMIN, GERANT.</li>
 *   <li>Annulation (soft delete) : SUPERADMIN, ADMIN.</li>
 *   <li>fromReservation : SUPERADMIN, ADMIN, GERANT, RECEPTION (workflow check-out).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/factures")
public class FactureController {

    private final FactureService service;
    private final PaiementService paiementService;
    private final FacturePdfService pdfService;

    public FactureController(FactureService service,
                             PaiementService paiementService,
                             FacturePdfService pdfService) {
        this.service = service;
        this.paiementService = paiementService;
        this.pdfService = pdfService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<FactureDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<FactureDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<FactureDto> create(@Valid @RequestBody FactureCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/emettre")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<FactureDto> emettre(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.emettre(id));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<FactureDto> annuler(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.annuler(id));
    }

    @PostMapping("/from-reservation/{reservationId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<FactureDto> fromReservation(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.fromReservation(reservationId));
    }

    /**
     * Tour 45 : transfert de lignes selectionnees d'une facture source vers
     * une facture cible. Refus si lignes deja payees ou factures terminales.
     */
    @PostMapping("/transferer-lignes")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<FactureDto> transfererLignes(@Valid @RequestBody TransfertLignesRequest request) {
        return ResponseEntity.ok(service.transfererLignes(request));
    }

    /**
     * Tour 45 : paiement granulaire des lignes selectionnees d'une facture.
     * Cree un Paiement + N AffectationPaiement (ventilation proportionnelle
     * au reste de chaque ligne, excedent credite sur compte client).
     * Route specifique exposee sous {@code /api/finance/factures/paiement-lignes}
     * (orienté facture) malgre la production cote {@link PaiementService}.
     */
    @PostMapping("/paiement-lignes")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<PaiementDto> paiementLignes(@Valid @RequestBody PaiementLignesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.paierLignes(request));
    }

    /**
     * Tour 46 : paiement global d'une reservation sans selection de lignes.
     * Le backend ventile automatiquement sur toutes les lignes facture non
     * payees de la reservation (FIFO sequentiel : par dateFacture ASC puis
     * ligneFactureId ASC). Excedent credite sur le compte client.
     */
    @PostMapping("/paiement-global")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<PaiementDto> paiementGlobal(@Valid @RequestBody PaiementGlobalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.payerGlobal(request));
    }

    /**
     * Tour 45 (fix dette technique) : retourne les vraies lignes-facture de
     * toutes les factures liees a une reservation, enrichies du montantPaye et
     * du reste. Consomme par la modale "Paiements" du calendrier hebergement
     * pour eliminer le proxy {@code factureId -> ligneFactureId} qui faussait
     * le paiement granulaire.
     */
    @GetMapping("/lignes-by-reservation/{reservationId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<List<LigneFactureRecapDto>> lignesByReservation(
            @PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.ok(service.findLignesRecapByReservation(reservationId));
    }

    /**
     * Tour 51bis - Bridge ServiceHotelier -&gt; LigneFacture.
     *
     * <p>Ajoute une ligne de type {@code SERVICE} a une facture existante,
     * resolue soit par {@code factureId}, soit par {@code reservationId}
     * (derniere facture non terminale rattachee a la reservation).</p>
     *
     * <p>Roles : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</p>
     */
    @PostMapping("/lignes-service")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<LigneFactureDto> addLigneService(
            @Valid @RequestBody LigneServiceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLigneService(request));
    }

    /**
     * Bloc B6 - Generation PDF de facture client conforme Mauritanie.
     *
     * <p>Reponse binaire {@code application/pdf} avec {@code Content-Disposition:
     * attachment; filename="FACTURE-{numeroFacture}.pdf"}. Le nom de fichier est
     * encode via {@link ContentDisposition} (RFC 5987) pour gerer les eventuels
     * caracteres non-ASCII du numero (resilience).</p>
     *
     * <p>Le PDF couvre tous les statuts (BROUILLON, EMISE, PARTIELLEMENT_PAYEE,
     * PAYEE, ANNULEE) - un filigrane diagonal est pose pour les statuts non
     * opposables (BROUILLON, ANNULEE).</p>
     *
     * <p>Roles : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC.</p>
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<byte[]> getPdf(@PathVariable("id") Long id) {
        // Tour 39 : recupere la facture d'abord pour avoir le numero dans le
        // filename - le findById applique deja le filtre tenant, donc une
        // facture cross-tenant retourne 404 avant la generation PDF.
        FactureDto facture = service.findById(id);
        byte[] body = pdfService.generate(id);

        String filename = "FACTURE-" + safeFilename(facture.numeroFacture()) + ".pdf";
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(cd);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private static String safeFilename(String s) {
        if (s == null || s.isBlank()) {
            return "sans-numero";
        }
        // Garde-fou minimal : remplace les caracteres dangereux pour filename
        // sans casser l'unicite (le numero comptable est deja [-0-9A-Z]+).
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
