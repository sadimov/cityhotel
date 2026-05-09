package com.cityprojects.citybackend.controller.restaurant;

import com.cityprojects.citybackend.dto.restaurant.ReimpressionTicketDto;
import com.cityprojects.citybackend.dto.restaurant.TicketDto;
import com.cityprojects.citybackend.service.restaurant.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API des tickets POS restaurant (Tour 24).
 *
 * <h3>Roles</h3>
 * <ul>
 *   <li>Tout : SUPERADMIN, ADMIN, GERANT, RESTAURANT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/restaurant/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping("/commande/{commandeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<List<TicketDto>> listerParCommande(
            @PathVariable("commandeId") Long commandeId) {
        return ResponseEntity.ok(service.listerParCommande(commandeId));
    }

    @PostMapping("/commande/{commandeId}/caisse")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<TicketDto> imprimerCaisse(
            @PathVariable("commandeId") Long commandeId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.imprimerTicketCaisse(commandeId));
    }

    @PostMapping("/commande/{commandeId}/cuisine")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<TicketDto> imprimerCuisine(
            @PathVariable("commandeId") Long commandeId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.imprimerTicketCuisine(commandeId));
    }

    @PostMapping("/commande/{commandeId}/reimpression")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RESTAURANT')")
    public ResponseEntity<TicketDto> reimprimer(
            @PathVariable("commandeId") Long commandeId,
            @Valid @RequestBody ReimpressionTicketDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.reimprimer(commandeId, dto.typeTicket(), dto.motif()));
    }
}
