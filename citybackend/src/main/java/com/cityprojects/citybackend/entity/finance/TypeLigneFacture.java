package com.cityprojects.citybackend.entity.finance;

/**
 * Type metier d'une ligne de facture.
 *
 * <p>Determine quelle FK metier ({@code nuitee_id}, {@code produit_id},
 * {@code commande_id}, {@code service_id}) doit etre renseignee. Regle metier
 * controlee dans {@code FactureService.creerLigne()} (le SQL ne l'impose pas
 * pour rester souple).</p>
 *
 * <ul>
 *   <li>{@code NUITEE} : 1 ligne = 1 nuit consommee. {@code nuitee_id} renseigne.</li>
 *   <li>{@code PRODUIT} : produit du stock vendu directement (mini-bar, kiosque...).
 *       {@code produit_id} renseigne (FK inventory.produits).</li>
 *   <li>{@code COMMANDE} : ticket POS restaurant. {@code commande_id} renseigne
 *       (FK reportee au tour restaurant).</li>
 *   <li>{@code SERVICE} : service hotelier (spa, transfert, blanchisserie).
 *       {@code service_id} reporte au tour finance-2.</li>
 *   <li>{@code DIVERS} : ligne libre saisie a la main, aucune FK metier.</li>
 * </ul>
 */
public enum TypeLigneFacture {
    NUITEE,
    PRODUIT,
    COMMANDE,
    SERVICE,
    DIVERS
}
