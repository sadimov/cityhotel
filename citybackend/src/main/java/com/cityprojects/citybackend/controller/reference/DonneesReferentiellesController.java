package com.cityprojects.citybackend.controller.reference;

import com.cityprojects.citybackend.dto.reference.DonneesReferentiellesDto;
import com.cityprojects.citybackend.service.reference.DonneesReferentiellesService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API du référentiel global (nationalités, types d'identification, ...).
 *
 * <h3>Rôles</h3>
 * <p>Lecture ouverte à tout utilisateur authentifié — c'est du référentiel
 * global non sensible, utilisé par tous les formulaires (création client,
 * fiche identité, etc.). Aucun endpoint d'écriture : seed Liquibase
 * uniquement (cf. changeset 061-seed-nationalites).</p>
 */
@RestController
@RequestMapping("/api/donnees-referentielles")
public class DonneesReferentiellesController {

    private final DonneesReferentiellesService service;

    public DonneesReferentiellesController(DonneesReferentiellesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DonneesReferentiellesDto>> findByCategorie(
            @RequestParam("categorie") String categorie) {
        return ResponseEntity.ok(service.findByCategorie(categorie));
    }
}
