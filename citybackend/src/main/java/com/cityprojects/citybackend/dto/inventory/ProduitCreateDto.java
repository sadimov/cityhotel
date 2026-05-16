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
 * <p>Le {@code stockActuel} est optionnel a la creation (default 0). Si fourni
 * et strictement positif, {@code ProduitServiceImpl.create()} pose ce stock
 * initial ET genere un {@code MouvementStock} de type {@code AJUSTEMENT} pour
 * traçabilite (audit trail). Apres la creation, le stock evolue uniquement
 * via les flux BC (entree) / BS (sortie) / ajustement manuel.</p>
 */
public record ProduitCreateDto(
        // codeProduit optionnel : si null/vide, ProduitServiceImpl.create() le
        // genere automatiquement via NumerotationService (TypeNumerotation.PROD).
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

        /**
         * Stock initial optionnel. Si fourni et > 0, genere un mouvement de
         * stock AJUSTEMENT pour audit trail. Default = 0.
         */
        @Min(value = 0, message = "error.produit.stockActuel.negative")
        Integer stockActuel,

        Long fournisseurPrincipalId,

        Boolean estFacturable) {

    /**
     * Constructeur de compat retro 10-args (sans {@code stockActuel}, pose à null).
     * Utilisé par les tests legacy et les call-sites externes qui n'ont pas encore
     * migré vers la signature 11-args. {@code stockActuel = null} signifie "ne pas
     * créer de mouvement initial".
     */
    public ProduitCreateDto(String codeProduit, String nomProduit, String description,
                            Long categorieId, String uniteMesure, BigDecimal prixUnitaire,
                            Integer seuilAlerte, Integer seuilCritique,
                            Long fournisseurPrincipalId, Boolean estFacturable) {
        this(codeProduit, nomProduit, description, categorieId, uniteMesure, prixUnitaire,
                seuilAlerte, seuilCritique, null, fournisseurPrincipalId, estFacturable);
    }
}
