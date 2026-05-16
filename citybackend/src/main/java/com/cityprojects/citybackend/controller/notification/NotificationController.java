package com.cityprojects.citybackend.controller.notification;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.dto.notification.NotificationDto;
import com.cityprojects.citybackend.service.hebergement.ReservationService;
import com.cityprojects.citybackend.service.inventory.ProduitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Notifications dynamiques pour l'utilisateur connecté.
 *
 * <p>Agrège des événements transverses (arrivées du jour, départs, stock
 * critique, check-ins en retard) en lecture seule depuis les services
 * existants. Multi-tenant via {@code @RequireTenant} + Hibernate
 * {@code @TenantId} sur les entités sous-jacentes.</p>
 *
 * <p><b>Périmètre V1</b> : agrégat éphémère (pas de persistance "lu/non-lu").
 * Tour ultérieur : persister une table {@code notifications} avec read state.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequireTenant
public class NotificationController {

    private final ReservationService reservationService;
    private final ProduitService produitService;

    public NotificationController(ReservationService reservationService,
                                   ProduitService produitService) {
        this.reservationService = reservationService;
        this.produitService = produitService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> list() {
        return ResponseEntity.ok(buildNotifications());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", buildNotifications().size()));
    }

    private List<NotificationDto> buildNotifications() {
        List<NotificationDto> result = new ArrayList<>();
        Instant now = Instant.now();

        // Arrivées prévues aujourd'hui (CONFIRMEE, dateArrivee=today)
        try {
            List<ReservationDto> arrivees = reservationService.findArriveesToday();
            for (ReservationDto r : arrivees) {
                result.add(new NotificationDto(
                        "RESERVATION_ARRIVEE",
                        "notifications.arrivee.title",
                        r.numeroReservation() != null ? r.numeroReservation() : ("#" + r.reservationId()),
                        "Check-in prevu aujourd'hui",
                        "bi-door-open",
                        "info",
                        "/hebergement/reservations/" + r.reservationId() + "/view",
                        now));
            }
        } catch (RuntimeException ignored) {
            // Si erreur d'un service, on continue avec les autres sources.
        }

        // Départs prévus aujourd'hui (ARRIVEE, dateDepart=today)
        try {
            List<ReservationDto> departs = reservationService.findDepartsToday();
            for (ReservationDto r : departs) {
                result.add(new NotificationDto(
                        "RESERVATION_DEPART",
                        "notifications.depart.title",
                        r.numeroReservation() != null ? r.numeroReservation() : ("#" + r.reservationId()),
                        "Check-out prevu aujourd'hui",
                        "bi-door-closed",
                        "warning",
                        "/hebergement/reservations/" + r.reservationId() + "/view",
                        now));
            }
        } catch (RuntimeException ignored) {
        }

        // Check-ins en retard (CONFIRMEE avec dateArrivee passee)
        try {
            List<ReservationDto> retards = reservationService.findCheckInsRetard();
            for (ReservationDto r : retards) {
                result.add(new NotificationDto(
                        "CHECKIN_RETARD",
                        "notifications.checkinRetard.title",
                        r.numeroReservation() != null ? r.numeroReservation() : ("#" + r.reservationId()),
                        "Check-in en retard",
                        "bi-clock-history",
                        "danger",
                        "/hebergement/reservations/" + r.reservationId() + "/view",
                        now));
            }
        } catch (RuntimeException ignored) {
        }

        // Produits en stock critique
        try {
            List<ProduitDto> critiques = produitService.findEnStockCritique();
            for (ProduitDto p : critiques) {
                result.add(new NotificationDto(
                        "STOCK_CRITIQUE",
                        "notifications.stockCritique.title",
                        p.nomProduit() != null ? p.nomProduit() : p.codeProduit(),
                        "Stock critique : " + p.stockActuel() + " " + (p.uniteMesure() != null ? p.uniteMesure() : ""),
                        "bi-exclamation-octagon",
                        "danger",
                        "/inventory/produits/" + p.produitId() + "/view",
                        now));
            }
        } catch (RuntimeException ignored) {
        }

        return result;
    }
}
