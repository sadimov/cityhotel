package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeModificationDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeMontantUpdateRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteesUpdateResultDto;
import com.cityprojects.citybackend.service.hebergement.NuiteeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API en lecture seule des nuitees (Tour 14).
 *
 * <p>Endpoints :
 * <ul>
 *   <li>{@code GET /api/hebergement/nuitees/reservation/{id}} : nuitees
 *       d'une reservation, par date croissante.</li>
 *   <li>{@code GET /api/hebergement/nuitees/chambre/{id}} : nuitees d'une
 *       chambre, paginees, tri stable {@code dateNuit DESC, nuiteeId ASC}.</li>
 * </ul>
 *
 * <p>Roles : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/NIGHTAUDIT
 * (lecture etendue, le night audit doit pouvoir auditer les nuitees).</p>
 *
 * <p>Aucun {@code hotelId} dans les payloads : tenant resolu via
 * {@link com.cityprojects.citybackend.common.tenant.TenantContext}.</p>
 */
@RestController
@RequestMapping("/api/hebergement/nuitees")
public class NuiteeController {

    private final NuiteeService nuiteeService;

    public NuiteeController(NuiteeService nuiteeService) {
        this.nuiteeService = nuiteeService;
    }

    @GetMapping("/reservation/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<NuiteeDto>> findByReservation(@PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(nuiteeService.findByReservation(reservationId));
    }

    @GetMapping("/chambre/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<Page<NuiteeDto>> findByChambre(@PathVariable("id") Long chambreId,
                                                          Pageable pageable) {
        return ResponseEntity.ok(nuiteeService.findByChambre(chambreId, pageable));
    }

    /**
     * Tour 45 : liste les nuitees <b>provisoires</b> d'une reservation
     * (eligibles a modification individuelle de montant).
     * Filtrage cote service : nuitees dont la facture parente n'est PAS
     * dans un etat terminal (PAYEE / ANNULEE).
     */
    @GetMapping("/reservation/{id}/provisoires")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<List<NuiteeModificationDto>> findProvisoires(
            @PathVariable("id") Long reservationId) {
        return ResponseEntity.ok(nuiteeService.findProvisoiresByReservation(reservationId));
    }

    /**
     * Tour 45 : mise a jour en lot des montants individuels des nuitees.
     * Le body est une liste de {@link NuiteeMontantUpdateRequest}.
     * Refus si la facture parente d'une ligne est PAYEE / ANNULEE.
     */
    @PatchMapping("/montants")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<NuiteesUpdateResultDto> updateMontants(
            @Valid @RequestBody List<NuiteeMontantUpdateRequest> requests) {
        return ResponseEntity.ok(nuiteeService.updateMontants(requests));
    }
}
