package com.cityprojects.citybackend.entity.restaurant;

/**
 * Cycle de vie d'un {@link ArticleMenu} dans le catalogue restaurant.
 *
 * <h3>Etats</h3>
 * <ul>
 *   <li>{@code ACTIF} : visible et commandable depuis le POS.</li>
 *   <li>{@code RUPTURE} : visible mais momentanement non servi (rupture de stock,
 *       ingredient indisponible). Le POS doit l'afficher grise et empecher
 *       l'ajout au panier.</li>
 *   <li>{@code INACTIF} : retire du catalogue (fin de saison, retrait definitif).
 *       Non affiche dans le POS, mais conserve pour l'historique des commandes
 *       passees.</li>
 * </ul>
 *
 * <h3>Transitions valides</h3>
 * <ul>
 *   <li>{@code ACTIF}    -&gt; {@code RUPTURE}, {@code INACTIF}</li>
 *   <li>{@code RUPTURE}  -&gt; {@code ACTIF}, {@code INACTIF}</li>
 *   <li>{@code INACTIF}  -&gt; {@code ACTIF} (reactivation)</li>
 * </ul>
 *
 * <p>Stockage : colonne {@code statut} VARCHAR(20) (cf. changeset
 * 004-create-restaurant-schema.xml). Les 7 caracteres de {@code RUPTURE} y
 * rentrent largement.</p>
 */
public enum StatutArticle {
    ACTIF,
    RUPTURE,
    INACTIF
}
