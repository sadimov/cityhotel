package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de demande de contre-passation d'une ecriture comptable.
 *
 * <p>Le motif est obligatoire (audit comptable) et journalise dans le libelle
 * de la nouvelle ecriture.</p>
 */
public record ContrePassationDto(
        @NotBlank
        @Size(min = 3, max = 500)
        String motif
) {}
