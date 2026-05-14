package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Requête de création (calcul) d'une déclaration TVA (B4).
 *
 * <p>Le service crée une déclaration en statut BROUILLON, calcule les
 * agrégats à partir des écritures VALIDEE de la période, et la
 * persiste. Si une déclaration existe déjà pour la même période, elle
 * est renvoyée telle quelle (idempotence).</p>
 *
 * @param dateDebut début de période inclus (obligatoire).
 * @param dateFin   fin de période inclus (obligatoire, &gt;= dateDebut).
 */
public record DeclarationTvaCreateDto(
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin
) {}
