/**
 * Catégorie de produit — entité du module inventory.
 *
 * Le backend impose un `codeCategorie` court (3-10 caractères) propre par
 * hôtel et un `nomCategorie` lisible. Multi-tenant : `hotelId` exposé en
 * lecture seule (lu via JWT côté serveur).
 */
export interface CategorieProduit {
  categorieId?: number;
  hotelId?: number;
  codeCategorie: string;
  nomCategorie: string;
  description?: string;
  actif?: boolean;
  dateCreation?: string;
  nombreProduits?: number;
  valeurStockCategorie?: number;
}
