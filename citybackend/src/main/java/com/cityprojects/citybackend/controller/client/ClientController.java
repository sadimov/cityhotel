package com.cityprojects.citybackend.controller.client;

import com.cityprojects.citybackend.dto.client.ClientCreateDto;
import com.cityprojects.citybackend.dto.client.ClientDto;
import com.cityprojects.citybackend.dto.client.ClientUpdateDto;
import com.cityprojects.citybackend.service.client.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API des clients (personnes physiques).
 * <p>
 * Aucun {@code hotelId} dans les payloads (CLAUDE.md racine §10).
 * Roles : meme matrice que {@link SocieteController}.
 *
 * <h2>Pas de DELETE physique</h2>
 * <p>A la difference de {@link SocieteController}, ce controller n'expose
 * <b>pas</b> de DELETE. Les clients sont
 * {@link ClientService#deactivate(Long)}-es (soft delete) pour conserver
 * l'historique des factures et des reservations. Un DELETE physique casserait
 * l'audit comptable et les FK aval (factures, paiements, nuitees). La regle
 * d'unicite email reste applicable apres desactivation : reactiver le client
 * existant plutot que d'en creer un nouveau.</p>
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ClientDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @GetMapping("/by-numero/{numero}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ClientDto> findByNumero(@PathVariable("numero") String numero) {
        return ResponseEntity.ok(clientService.findByNumeroClient(numero));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<ClientDto>> search(
            @RequestParam(value = "q", required = false) String recherche,
            Pageable pageable) {
        return ResponseEntity.ok(clientService.search(recherche, pageable));
    }

    @GetMapping("/by-societe/{societeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<ClientDto>> findBySociete(
            @PathVariable("societeId") Long societeId, Pageable pageable) {
        return ResponseEntity.ok(clientService.findBySociete(societeId, pageable));
    }

    @GetMapping("/without-societe")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Page<ClientDto>> findWithoutSociete(Pageable pageable) {
        return ResponseEntity.ok(clientService.findWithoutSociete(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<ClientDto> create(@Valid @RequestBody ClientCreateDto dto) {
        ClientDto created = clientService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<ClientDto> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody ClientUpdateDto dto) {
        return ResponseEntity.ok(clientService.update(id, dto));
    }

    @PostMapping("/{id}/societe/{societeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<ClientDto> assignToSociete(@PathVariable("id") Long id,
                                                     @PathVariable("societeId") Long societeId) {
        return ResponseEntity.ok(clientService.assignToSociete(id, societeId));
    }

    @PostMapping("/{id}/dissociate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION')")
    public ResponseEntity<ClientDto> dissociate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(clientService.assignToSociete(id, null));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> deactivate(@PathVariable("id") Long id) {
        clientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT')")
    public ResponseEntity<Void> reactivate(@PathVariable("id") Long id) {
        clientService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * KPI dashboard accueil — nombre de clients créés à la date demandée
     * (par défaut aujourd'hui dans la timezone serveur).
     * Réponse : {@code {"count": 12, "date": "2026-05-17"}}.
     */
    @GetMapping("/nouveaux-du-jour")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<Map<String, Object>> countNouveauxDuJour(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        long count = clientService.countNouveauxDuJour(date);
        LocalDate effective = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(Map.of("count", count, "date", effective.toString()));
    }
}
