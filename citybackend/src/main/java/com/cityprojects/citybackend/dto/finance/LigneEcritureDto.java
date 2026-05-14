package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.SensLigne;

import java.math.BigDecimal;

/**
 * DTO de lecture d'une ligne d'ecriture comptable.
 */
public record LigneEcritureDto(
        Long id,
        int ordre,
        String compteCode,
        String libelle,
        SensLigne sens,
        BigDecimal montant,
        String compteAuxiliaireRef
) {}
