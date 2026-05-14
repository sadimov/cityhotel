package com.cityprojects.citybackend.controller.reporting;

import com.cityprojects.citybackend.dto.reporting.BcPendantDto;
import com.cityprojects.citybackend.dto.reporting.MouvementValoriseDto;
import com.cityprojects.citybackend.dto.reporting.RotationProduitDto;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.service.reporting.BcPendantsRotationReportService;
import com.cityprojects.citybackend.service.reporting.MouvementsValorisesReportService;
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
 * REST API inventory reporting P2 (Tour 41) :
 * <ul>
 *   <li>R-INV-002 Mouvements valorises</li>
 *   <li>R-INV-003 BC pendants + rotation produits</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/inventory")
public class InventoryReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ROLES_INV = "hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')";

    private final MouvementsValorisesReportService mouvementsService;
    private final BcPendantsRotationReportService bcRotationService;

    public InventoryReportController(MouvementsValorisesReportService mouvementsService,
                                     BcPendantsRotationReportService bcRotationService) {
        this.mouvementsService = mouvementsService;
        this.bcRotationService = bcRotationService;
    }

    @GetMapping("/mouvements-valorises")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<MouvementValoriseDto> getMouvements(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "type", required = false) TypeMouvementStock type) {
        return ResponseEntity.ok(mouvementsService.computeMouvements(from, to, type));
    }

    @GetMapping(value = "/mouvements-valorises/export.xlsx")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<byte[]> exportMouvementsXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "type", required = false) TypeMouvementStock type) {
        return attachment("mouvements-valorises.xlsx", xlsxMediaType(),
                mouvementsService.exportXlsx(from, to, type));
    }

    @GetMapping("/bc-pendants")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<List<BcPendantDto>> getBcPendants() {
        return ResponseEntity.ok(bcRotationService.findBcPendants());
    }

    @GetMapping(value = "/bc-pendants/export.xlsx")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<byte[]> exportBcPendantsXlsx() {
        return attachment("bc-pendants.xlsx", xlsxMediaType(),
                bcRotationService.exportBcPendantsXlsx());
    }

    @GetMapping("/rotation-produits")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<List<RotationProduitDto>> getRotation(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(bcRotationService.computeRotation(from, to));
    }

    @GetMapping(value = "/rotation-produits/export.xlsx")
    @PreAuthorize(ROLES_INV)
    public ResponseEntity<byte[]> exportRotationXlsx(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attachment("rotation-produits.xlsx", xlsxMediaType(),
                bcRotationService.exportRotationXlsx(from, to));
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
