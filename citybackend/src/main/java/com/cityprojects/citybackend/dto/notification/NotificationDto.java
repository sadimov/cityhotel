package com.cityprojects.citybackend.dto.notification;

import java.time.Instant;

/**
 * DTO de notification utilisateur (header bell). Agrégat lecture-seule des
 * événements pertinents pour l'utilisateur connecté (arrivées du jour,
 * départs, stock critique, factures impayées, tâches en retard...).
 *
 * <p>{@code icon} et {@code severity} (info|warning|danger|success) sont
 * portés par le DTO pour permettre au front d'appliquer un style sans avoir
 * à mapper le {@code type} en dur.</p>
 */
public record NotificationDto(
        String type,
        String titleKey,
        String message,
        String detail,
        String icon,
        String severity,
        String link,
        Instant timestamp) {
}
