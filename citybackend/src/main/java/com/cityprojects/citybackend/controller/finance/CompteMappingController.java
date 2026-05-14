package com.cityprojects.citybackend.controller.finance;

import com.cityprojects.citybackend.dto.finance.CompteMappingDto;
import com.cityprojects.citybackend.dto.finance.CompteMappingUpdateDto;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.service.finance.CompteMappingService;
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
 * REST API du mapping comptable hotel (couple {@code typeEvenement} -&gt;
 * {@code compteCode} du PCG).
 *
 * <p>Accès réservé {@code SUPERADMIN, ADMIN, GERANT} - configuration hotel
 * réservée aux profils administratifs.</p>
 */
@RestController
@RequestMapping("/api/finance/compte-mapping")
public class CompteMappingController {

    private final CompteMappingService service;

    public CompteMappingController(CompteMappingService service) {
        this.service = service;
    }

    /** Liste les mappings (personnalises + defauts codes pour les types non encore mappes). */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<List<CompteMappingDto>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /** Met a jour le mapping d'un evenement comptable pour l'hotel courant. */
    @PutMapping("/{typeEvenement}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<CompteMappingDto> updateMapping(
            @PathVariable("typeEvenement") TypeEvenementComptable typeEvenement,
            @Valid @RequestBody CompteMappingUpdateDto body) {
        return ResponseEntity.ok(service.updateMapping(typeEvenement, body.compteCode()));
    }
}
