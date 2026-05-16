/**
 * Produit — entité du module inventory.
 *
 * Le `statutStock` est un champ dérivé renvoyé par le backend, calculé à
 * partir de `stockActuel` vs `seuilAlerte`/`seuilCritique`. Le client ne le
 * définit jamais lors d'un POST/PUT.
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — jamais envoyé par le
 * client (cf. CLAUDE.md racine §6.1).
 */
export type StatutStock = 'NORMAL' | 'ALERTE' | 'CRITIQUE';

export interface Produit {
  produitId?: number;
  hotelId?: number;
  /** Optionnel en création : le backend auto-génère via NumerotationService.PROD si absent. */
  codeProduit?: string;
  nomProduit: string;
  description?: string;
  categorieId: number;
  uniteMesure: string;
  prixUnitaire: number;
  seuilAlerte: number;
  seuilCritique: number;
  stockActuel: number;
  fournisseurPrincipalId?: number;
  estFacturable?: boolean;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;
  nomCategorie?: string;
  nomFournisseurPrincipal?: string;
  valeurStock?: number;
  statutStock?: StatutStock;
  quantiteCommandee?: number;
  quantiteReservee?: number;
}

/**
 * Payload pour l'ajustement manuel d'un stock (raison + commentaire
 * obligatoires côté backend pour traçabilité).
 */
export interface AjustementStock {
  produitId: number;
  nouveauStock: number;
  raisonAjustement: string;
  commentaire?: string;
}

/**
 * Filtres optionnels pour la liste paginée des produits.
 */
export interface FiltresProduits {
  search?: string;
  categorieId?: number;
  statutStock?: StatutStock;
}
