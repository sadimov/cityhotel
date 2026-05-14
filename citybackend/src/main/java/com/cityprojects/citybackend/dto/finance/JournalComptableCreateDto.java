package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeJournal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de creation d'un journal comptable.
 *
 * <p>Le code (3-5 caracteres alphanumeriques majuscules) est l'identifiant
 * stable utilise comme discriminant pour la numerotation des ecritures (voir
 * {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#JRN}).
 * Doit etre unique au sein d'un hotel.</p>
 */
public record JournalComptableCreateDto(
        @NotBlank
        @Size(min = 1, max = 5)
        @Pattern(regexp = "^[A-Z0-9]{1,5}$", message = "{error.journal.codeFormat}")
        String code,

        @NotBlank
        @Size(min = 1, max = 100)
        String libelle,

        @NotNull
        TypeJournal type
) {}
