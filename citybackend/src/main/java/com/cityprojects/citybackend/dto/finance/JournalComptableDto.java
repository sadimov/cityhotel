package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeJournal;

/**
 * DTO de lecture d'un journal comptable.
 *
 * <p>Le {@code hotelId} n'est pas expose : reserve au {@code TenantContext}.</p>
 */
public record JournalComptableDto(
        Long id,
        String code,
        String libelle,
        TypeJournal type,
        boolean actif
) {}
