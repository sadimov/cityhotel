package com.cityprojects.citybackend.controller.hebergement;

import com.cityprojects.citybackend.dto.finance.RecapPaiementsReservationDto;
import com.cityprojects.citybackend.dto.hebergement.CancelReservationDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.dto.hebergement.ChangerChambreRequest;
import com.cityprojects.citybackend.dto.hebergement.CheckOutExpressRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.RechercheDisponibiliteRequest;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.service.finance.ReservationFinanceService;
import com.cityprojects.citybackend.service.hebergement.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API des reservations.
 *
 * <p>Roles :
 * <ul>
 *   <li>Lecture : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC/NIGHTAUDIT.</li>
 *   <li>Creation/check-in/check-out/cancel/update/delete : SUPERADMIN/ADMIN/GERANT/RECEPTION/RESREC.</li>
 *   <li>{@code rechercher-disponibilite} : ouvert aussi a NIGHTAUDIT (consultation).</li>
 * </ul>
 *
 * <p>Aucun {@code hotelId} ni {@code userId} dans les payloads
 * (CLAUDE.md racine §10) : {@code hotelId} resolu par Hibernate via
 * {@code @TenantId}, {@code userId} extrait du SecurityContext.</p>
 *
 * <p>Tour 14 audit B1 : prefixe route migre de {@code /api/reservations} vers
 * {@code /api/hebergement/reservations}. B2 : ajout des endpoints
 * arrivees-today / departs-today / en-cours / check-ins-retard / rechercher /
 * rechercher-disponibilite / update / delete.</p>
 */
@RestController
@RequestMapping("/api/hebergement/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationFinanceService reservationFinanceService;

    public ReservationController(ReservationService reservationService,
                                  ReservationFinanceService reservationFinanceService) {
        this.reservationService = reservationService;
        this.reservationFinanceService = reservationFinanceService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<ReservationDto> findById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationService.findById(id));
    }

    @GetMapping("/by-numero/{numero}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<ReservationDto> findByNumero(@PathVariable("numero") String numero) {
        return ResponseEntity.ok(reservationService.findByNumero(numero));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<Page<ReservationDto>> findAll(
            @RequestParam(value = "statut", required = false) StatutReservation statut,
            @RequestParam(value = "clientId", required = false) Long clientId,
            Pageable pageable) {
        return ResponseEntity.ok(reservationService.findAll(statut, clientId, pageable));
    }

    @GetMapping("/by-client/{clientId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<Page<ReservationDto>> findByClient(@PathVariable("clientId") Long clientId,
                                                              Pageable pageable) {
        return ResponseEntity.ok(reservationService.findByClient(clientId, pageable));
    }

    @GetMapping("/{id}/nuitees")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<NuiteeDto>> findNuitees(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationService.findNuitees(id));
    }

    @GetMapping("/arrivees-today")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<ReservationDto>> findArriveesToday() {
        return ResponseEntity.ok(reservationService.findArriveesToday());
    }

    @GetMapping("/departs-today")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<ReservationDto>> findDepartsToday() {
        return ResponseEntity.ok(reservationService.findDepartsToday());
    }

    @GetMapping("/en-cours")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<ReservationDto>> findEnCours() {
        return ResponseEntity.ok(reservationService.findEnCours());
    }

    @GetMapping("/check-ins-retard")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<ReservationDto>> findCheckInsRetard() {
        return ResponseEntity.ok(reservationService.findCheckInsRetard());
    }

    @GetMapping("/rechercher")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<Page<ReservationDto>> rechercher(@RequestParam("terme") String terme,
                                                            Pageable pageable) {
        return ResponseEntity.ok(reservationService.rechercher(terme, pageable));
    }

    @PostMapping("/rechercher-disponibilite")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC','NIGHTAUDIT')")
    public ResponseEntity<List<ChambreDto>> rechercherDisponibilite(
            @Valid @RequestBody RechercheDisponibiliteRequest request) {
        return ResponseEntity.ok(reservationService.rechercherDisponibilite(request));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> create(@Valid @RequestBody ReservationCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> update(@PathVariable("id") Long id,
                                                  @Valid @RequestBody ReservationCreateDto dto) {
        return ResponseEntity.ok(reservationService.update(id, dto));
    }

    /**
     * Annulation logique (soft-delete). Règle métier : seul ADMIN/SUPERADMIN
     * peut annuler une réservation (cf. règle hébergement).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ReservationDto> delete(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationService.delete(id));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> checkIn(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationService.checkIn(id));
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> checkOut(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationService.checkOut(id));
    }

    /**
     * Tour 45 : check-out express avec transfert du reste-a-payer sur le
     * compte d'une societe (la societe assume la dette).
     */
    @PostMapping("/{id}/check-out-express")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> checkOutExpress(@PathVariable("id") Long id,
                                                           @Valid @RequestBody CheckOutExpressRequest request) {
        return ResponseEntity.ok(reservationService.checkOutExpress(id, request));
    }

    /**
     * Annulation explicite avec motif. Règle métier : seul ADMIN/SUPERADMIN
     * peut annuler une réservation (cf. règle hébergement).
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<ReservationDto> cancel(@PathVariable("id") Long id,
                                                  @Valid @RequestBody CancelReservationDto dto) {
        return ResponseEntity.ok(reservationService.cancel(id, dto.motif()));
    }

    /**
     * Tour 44 Phase 1 : recap factures + paiements pour une reservation.
     * Sert l'onglet "Paiements" du calendrier des reservations.
     */
    @GetMapping("/{id}/paiements-recap")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<RecapPaiementsReservationDto> paiementsRecap(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reservationFinanceService.getRecapForReservation(id));
    }

    /**
     * Tour 44 Phase 1 : changement de chambre pour une reservation existante.
     * Verifie l'absence de conflit sur la nouvelle chambre, met a jour les
     * pivots et les nuitees non-facturees, libere l'ancienne chambre.
     */
    @PatchMapping("/{id}/chambre")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','RECEPTION','RESREC')")
    public ResponseEntity<ReservationDto> changerChambre(@PathVariable("id") Long id,
                                                          @Valid @RequestBody ChangerChambreRequest request) {
        return ResponseEntity.ok(reservationService.changerChambre(id, request));
    }
}
