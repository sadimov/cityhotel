package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.FolioDto;
import com.cityprojects.citybackend.service.finance.OperationCompteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API du compte AUXILIAIRE CLIENT (Tour 46).
 *
 * <p>Expose un endpoint folio (extrait de compte filtre par plage de dates)
 * consomme par la modale paiement frontend Tour 46. La verification d'isolation
 * tenant est portee par Hibernate {@code @TenantId} + l'aspect
 * {@code @RequireTenant} pose au niveau service.</p>
 */
@RestController
@RequestMapping("/api/finance/comptes")
public class CompteController {

    private final OperationCompteService operationCompteService;

    public CompteController(OperationCompteService operationCompteService) {
        this.operationCompteService = operationCompteService;
    }

    /**
     * Tour 46 — Folio (extrait de compte) du compte auxiliaire d'un client
     * filtre sur une plage de dates (ex. plage de dates d'une reservation).
     *
     * <p>Retourne :
     * <ul>
     *   <li>les operations DEBIT/CREDIT filtrees,</li>
     *   <li>le solde d'ouverture (avant {@code dateDebut}),</li>
     *   <li>le solde de cloture (apres {@code dateFin}),</li>
     *   <li>les agreges {@code totalDebits} / {@code totalCredits}.</li>
     * </ul>
     *
     * <p>Si {@code dateDebut} et/ou {@code dateFin} ne sont pas fournis :
     * pas de borne (toute la chronologie est incluse). Cas usage frontend
     * Tour 46 : toujours fournir la plage de la reservation.</p>
     */
    @GetMapping("/client/{clientId}/folio")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<FolioDto> folio(
            @PathVariable("clientId") Long clientId,
            @RequestParam(value = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(value = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(operationCompteService.findFolio(clientId, dateDebut, dateFin));
    }
}
