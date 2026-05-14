package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;

/**
 * DTO de lecture d'un mapping comptable hotel (couple type d'evenement -&gt;
 * code de compte PCG).
 *
 * <p>Le {@code hotelId} n'est pas expose : reservé au {@code TenantContext}.</p>
 *
 * @param typeEvenement     evenement comptable couvert
 * @param compteCode        code du compte PCG cible (ex. {@code "706100"})
 * @param compteLibelle     libelle du compte (denormalisé depuis le PCG pour
 *                          eviter un round-trip cote front)
 * @param actif             {@code true} si le mapping est actif
 * @param defaut            {@code true} si le mapping est synthétique
 *                          (defaut codé, pas d'enregistrement en base) ;
 *                          {@code false} pour un mapping personnalisé
 */
public record CompteMappingDto(
        TypeEvenementComptable typeEvenement,
        String compteCode,
        String compteLibelle,
        boolean actif,
        boolean defaut
) {}
