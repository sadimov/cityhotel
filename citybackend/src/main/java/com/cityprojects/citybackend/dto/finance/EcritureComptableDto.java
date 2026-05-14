package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.StatutEcriture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de lecture d'une ecriture comptable (en-tete + lignes).
 *
 * <p>Le {@code hotelId} n'est pas expose. Les liens vers le journal et
 * l'exercice sont denormalises (id + libelle/code) pour eviter un round-trip
 * cote front sans pour autant trainer toute l'entite.</p>
 */
public record EcritureComptableDto(
        Long id,
        String numero,
        LocalDate dateComptable,
        LocalDate datePiece,
        Long journalId,
        String journalCode,
        String journalLibelle,
        Long exerciceId,
        String exerciceCode,
        String libelle,
        String reference,
        StatutEcriture statut,
        Long contrePasseeParId,
        Long ecritureSourceId,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        List<LigneEcritureDto> lignes,
        Instant createdAt,
        String createdBy
) {}
