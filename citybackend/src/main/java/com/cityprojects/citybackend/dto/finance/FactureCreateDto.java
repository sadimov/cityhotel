package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeFacture;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de creation d'une facture.
 *
 * <p>Le {@code numeroFacture} est genere par le service via NumerotationService.
 * Le {@code hotelId} est extrait de TenantContext.</p>
 *
 * <p>{@code lignes} peut etre vide a la creation : on cree alors une facture
 * BROUILLON sans lignes, qu'on enrichit ensuite. Si fourni, les montants sont
 * recalcules automatiquement.</p>
 */
public record FactureCreateDto(
        TypeFacture typeFacture,
        Long compteId,
        Long clientId,
        Long societeId,
        Long reservationId,
        Long fournisseurId,
        LocalDate dateFacture,
        LocalDate dateEcheance,
        @Size(max = 3) String devise,
        String commentaires,
        @Valid List<LigneFactureCreateDto> lignes) {
}
