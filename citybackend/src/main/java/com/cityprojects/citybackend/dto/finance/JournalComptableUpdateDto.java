package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeJournal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de mise a jour d'un journal comptable.
 *
 * <p>Le {@code code} n'est PAS modifiable apres creation (sert de discriminant
 * pour la numerotation des ecritures - changer le code casserait la sequence).
 * Seuls {@code libelle} et {@code type} sont modifiables.</p>
 */
public record JournalComptableUpdateDto(
        @NotBlank
        @Size(min = 1, max = 100)
        String libelle,

        @NotNull
        TypeJournal type
) {}
