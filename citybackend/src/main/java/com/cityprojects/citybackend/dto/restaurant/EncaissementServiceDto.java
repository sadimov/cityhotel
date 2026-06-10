package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Ligne service hotelier portee par {@link EncaissementCommandeDto} (Tour 70).
 *
 * <p>Permet d'ajouter une ligne SERVICE a la facture creee par
 * {@code encaisserComptant()} avant l'emission, pour que la facture imprimee
 * contienne articles + services dans une transaction atomique.</p>
 *
 * <p>Le {@code prixUnitaire} est obligatoire : on snapshote le prix vu par
 * l'operateur POS (le catalogue {@code ServiceHotelier} peut evoluer apres).
 * Le {@code libelle} override optionnel (sinon le nom du service est utilise).</p>
 */
public record EncaissementServiceDto(
        @NotNull(message = "error.encaissement.service.serviceId.required")
        Long serviceId,

        @NotNull(message = "error.encaissement.service.quantite.required")
        @DecimalMin(value = "0.001", message = "error.ligneFacture.quantite.positive")
        BigDecimal quantite,

        @NotNull(message = "error.encaissement.service.prixUnitaire.required")
        @DecimalMin(value = "0.0", message = "error.ligneFacture.prix.negative")
        BigDecimal prixUnitaire,

        @Size(max = 200)
        String libelle) {
}
