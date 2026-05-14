package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.TauxTvaConfigDto;
import com.cityprojects.citybackend.dto.finance.TauxTvaConfigUpdateDto;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.service.finance.TauxTvaConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API du paramétrage TVA hotel (B4).
 *
 * <p>Accès :</p>
 * <ul>
 *   <li>Lecture : SUPERADMIN, ADMIN, GERANT.</li>
 *   <li>Écriture : SUPERADMIN, ADMIN (configuration fiscale sensible).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/tva/config")
public class TauxTvaConfigController {

    private final TauxTvaConfigService service;

    public TauxTvaConfigController(TauxTvaConfigService service) {
        this.service = service;
    }

    /** Liste les configurations TVA (personnalisées + défauts codés). */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<List<TauxTvaConfigDto>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /** Lecture par type de service. */
    @GetMapping("/{typeService}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<TauxTvaConfigDto> findByType(
            @PathVariable("typeService") TypeServiceTva typeService) {
        return ResponseEntity.ok(service.findByType(typeService));
    }

    /** Mise à jour (upsert) du taux TVA d'un type de service. */
    @PutMapping("/{typeService}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<TauxTvaConfigDto> update(
            @PathVariable("typeService") TypeServiceTva typeService,
            @Valid @RequestBody TauxTvaConfigUpdateDto body) {
        return ResponseEntity.ok(service.update(
                typeService, body.taux(), body.actif(), body.libelle()));
    }
}
