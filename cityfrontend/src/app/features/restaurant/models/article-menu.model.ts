/**
 * ArticleMenu — entité du module restaurant (catalogue).
 *
 * Source de vérité (Tour 6, arbitrage) : éclaté de la spec
 * `RESTAURANT/resultat_chatgpt/cityfrontend_restaurant_module/.../models/restaurant-models.ts`.
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — jamais envoyé par le
 * client (cf. CLAUDE.md racine §6.1).
 *
 * `allergenes` est stocké en JSON string côté backend (liste sérialisée).
 * Le champ `imageUrl` est une URL relative (montée par le serveur de
 * fichiers de l'hôtel courant) — le client ne construit pas l'URL.
 */
export interface ArticleMenu {
  articleId?: number;
  hotelId?: number;
  categorieId: number;
  codeArticle?: string;
  nomArticle: string;
  nomArticleEn?: string;
  nomArticleAr?: string;
  description?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  prix: number;
  coutIngredients?: number;
  tempsPreparation?: number;
  /** JSON string : ex. `'["gluten","lactose"]'`. */
  allergenes?: string;
  imageUrl?: string;
  disponible?: boolean;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;
  /** Champ dérivé renvoyé par le backend (lecture seule). */
  nomCategorie?: string;
}

/**
 * Filtres optionnels pour la liste paginée des articles du menu.
 *
 * `disponible` distingue les articles temporairement en rupture
 * (still actif, mais pas vendable) ; `actif` distingue les articles
 * désactivés du catalogue.
 */
export interface FiltresArticlesMenus {
  search?: string;
  categorieId?: number;
  disponible?: boolean;
  actif?: boolean;
}
