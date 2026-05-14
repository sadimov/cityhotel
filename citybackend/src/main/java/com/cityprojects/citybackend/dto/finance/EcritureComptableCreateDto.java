package com.cityprojects.citybackend.dto.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de creation d'une ecriture comptable en partie double.
 *
 * <p>Le service ({@code EcritureComptableServiceImpl#creer}) valide :
 * <ul>
 *   <li>au moins 2 lignes,</li>
 *   <li>somme des debits == somme des credits (tolerance 0.01 MRU),</li>
 *   <li>chaque {@code compteCode} existe et est {@code utilisable=true}
 *       dans le PCG,</li>
 *   <li>l'exercice contenant {@code dateComptable} est OUVERT,</li>
 *   <li>le journal identifie par {@code journalCode} existe et est actif.</li>
 * </ul>
 * Si {@code datePiece} est null, le service le force a {@code dateComptable}.</p>
 */
public record EcritureComptableCreateDto(
        @NotNull
        LocalDate dateComptable,

        LocalDate datePiece,

        @NotBlank
        @Size(min = 1, max = 5)
        String journalCode,

        @NotBlank
        @Size(min = 1, max = 500)
        String libelle,

        @Size(max = 50)
        String reference,

        @NotNull
        @NotEmpty
        @Valid
        List<LigneEcritureCreateDto> lignes
) {}
