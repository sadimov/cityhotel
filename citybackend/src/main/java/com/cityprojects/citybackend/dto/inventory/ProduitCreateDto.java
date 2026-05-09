package com.cityprojects.citybackend.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO d'entree pour la creation d'un produit.
 *
 * <p>Le {@code stockActuel} initial est positionne a 0 cote service - il evolue
 * uniquement via les flux BC (entree) et BS (sortie) ou ajustement manuel.</p>
 */
public record ProduitCreateDto(
        @NotBlank(message = "error.produit.code.blank")
        @Size(max = 20, message = "error.produit.code.tooLong")
        String codeProduit,

        @NotBlank(message = "error.produit.nom.blank")
        @Size(max = 255, message = "error.produit.nom.tooLong")
        String nomProduit,

        String description,

        @NotNull(message = "error.produit.categorie.required")
        Long categorieId,

        @NotBlank(message = "error.produit.unite.blank")
        @Size(max = 20, message = "error.produit.unite.tooLong")
        String uniteMesure,

        @DecimalMin(value = "0.00", message = "error.produit.prix.negative")
        BigDecimal prixUnitaire,

        @Min(value = 0, message = "error.produit.seuilAlerte.negative")
        Integer seuilAlerte,

        @Min(value = 0, message = "error.produit.seuilCritique.negative")
        Integer seuilCritique,

        Long fournisseurPrincipalId,

        Boolean estFacturable) {
}
