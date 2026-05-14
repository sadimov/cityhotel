package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.StatutDeclarationTva;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de lecture d'une déclaration TVA (B4).
 *
 * @param id                   id de la déclaration.
 * @param dateDebut            début de période inclus.
 * @param dateFin              fin de période inclus.
 * @param totalTvaCollectee    somme des CREDIT 445700 sur la période.
 * @param totalTvaDeductible   somme des DEBIT 445600 sur la période.
 * @param totalTvaADecaisser   collectée - déductible (négatif = crédit reportable).
 * @param statut               cycle de vie (BROUILLON / VALIDEE).
 * @param exerciceId           id de l'exercice de rattachement (peut être null).
 * @param ecritureLiquidationId id de l'écriture comptable de liquidation
 *                              (null tant que statut BROUILLON).
 * @param dateValidation       date de validation (null tant que BROUILLON).
 * @param valideePar           username Spring Security (null tant que BROUILLON).
 */
public record DeclarationTvaDto(
        Long id,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal totalTvaCollectee,
        BigDecimal totalTvaDeductible,
        BigDecimal totalTvaADecaisser,
        StatutDeclarationTva statut,
        Long exerciceId,
        Long ecritureLiquidationId,
        LocalDate dateValidation,
        String valideePar
) {}
