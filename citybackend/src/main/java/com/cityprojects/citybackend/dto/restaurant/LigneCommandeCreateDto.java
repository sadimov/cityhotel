package com.cityprojects.citybackend.dto.restaurant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO d'entree pour creer une ligne de commande POS (Tour 24).
 *
 * <p>{@code articleId} doit referencer un {@code ArticleMenu} actif et
 * disponible du tenant courant. {@code libelle} et {@code prixUnitaire} sont
 * snapshotes cote service depuis l'article (pas pris du DTO pour eviter la
 * fraude - le client ne peut pas imposer un prix).</p>
 *
 * <p>Si {@code prixUnitaire} est explicitement fourni dans le DTO, il
 * <b>override</b> le prix catalogue (cas reduction operateur). Le service
 * journalisera l'ecart.</p>
 */
public record LigneCommandeCreateDto(
        @NotNull(message = "error.ligneCommande.articleId.required")
        Long articleId,

        @NotNull(message = "error.ligneCommande.quantite.required")
        @DecimalMin(value = "0.01", message = "error.ligneCommande.quantite.positive")
        BigDecimal quantite,

        /** Optionnel : si renseigne, override le prix catalogue. */
        @DecimalMin(value = "0.0", message = "error.ligneCommande.prix.negative")
        BigDecimal prixUnitaire,

        String notesCuisine) {
}
