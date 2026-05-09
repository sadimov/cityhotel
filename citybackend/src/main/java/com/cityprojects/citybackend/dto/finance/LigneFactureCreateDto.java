package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO d'entree pour creer une ligne de facture.
 *
 * <p>Une seule des FK metier ({@code nuiteeId}, {@code produitId},
 * {@code commandeId}, {@code serviceId}) doit etre renseignee selon
 * {@code typeLigne} (regle metier validee dans le service).</p>
 */
public record LigneFactureCreateDto(
        @NotNull TypeLigneFacture typeLigne,
        Long nuiteeId,
        Long produitId,
        Long commandeId,
        Long serviceId,
        @NotBlank @Size(max = 500) String libelle,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantite,
        @NotNull @DecimalMin(value = "0.0") BigDecimal prixUnitaire,
        BigDecimal tauxTva,
        LocalDate datePrestation) {
}
