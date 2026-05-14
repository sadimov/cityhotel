package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.SensLigne;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de creation d'une ligne d'ecriture comptable.
 *
 * <p>Le {@code montant} est toujours positif - le sens
 * ({@link SensLigne#DEBIT}/{@link SensLigne#CREDIT}) porte l'information
 * directionnelle.</p>
 */
public record LigneEcritureCreateDto(
        /** Ordre d'affichage (1, 2, 3...). 0 ou null traite comme "indifferent". */
        Integer ordre,

        @NotBlank
        @Size(min = 1, max = 10)
        String compteCode,

        @Size(max = 500)
        String libelle,

        @NotNull
        SensLigne sens,

        @NotNull
        @DecimalMin(value = "0.01", message = "{error.ligneEcriture.montantPositif}")
        BigDecimal montant,

        @Size(max = 50)
        String compteAuxiliaireRef
) {}
