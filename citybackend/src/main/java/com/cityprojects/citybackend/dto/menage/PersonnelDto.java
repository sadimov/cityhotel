package com.cityprojects.citybackend.dto.menage;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO de sortie pour {@link com.cityprojects.citybackend.entity.menage.Personnel}.
 *
 * <p><b>NE CONTIENT PAS</b> {@code hotelId} (resolu via TenantContext, jamais
 * expose au client).</p>
 */
public record PersonnelDto(
        Long personnelId,
        String numeroEmploye,
        String prenom,
        String nom,
        String nomComplet,
        String telephone,
        String email,
        LocalDate dateEmbauche,
        String specialites,
        Boolean actif,
        Instant createdAt,
        Instant updatedAt) {
}
