package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeServiceTva;

import java.math.BigDecimal;

/**
 * DTO de lecture d'une configuration TVA (B4).
 *
 * @param typeService type de service (alignement métier TypeLigneFacture).
 * @param taux        taux TVA en pourcentage (ex. 16.00).
 * @param actif       {@code true} si la configuration est active.
 * @param libelle     libellé descriptif.
 * @param defaut      {@code true} si DTO synthétique (défaut codé, pas
 *                    d'enregistrement en base) ; {@code false} si la
 *                    configuration est personnalisée et persistée.
 */
public record TauxTvaConfigDto(
        TypeServiceTva typeService,
        BigDecimal taux,
        boolean actif,
        String libelle,
        boolean defaut
) {}
