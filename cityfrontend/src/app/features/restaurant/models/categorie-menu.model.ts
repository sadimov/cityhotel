/**
 * CategorieMenu — entité du module restaurant (catalogue).
 *
 * Source de vérité (Tour 6, arbitrage) : la spec
 * `RESTAURANT/resultat_chatgpt/cityfrontend_restaurant_module/.../models/restaurant-models.ts`
 * a été éclatée en `categorie-menu.model.ts` + `article-menu.model.ts`
 * conformément à la convention `cityfrontend/CLAUDE.md` (1 modèle par
 * fichier).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — jamais envoyé par le
 * client (cf. CLAUDE.md racine §6.1). Le champ est conservé en `?` pour la
 * lecture mais n'est jamais sérialisé en POST/PUT.
 *
 * Convention : `nomCategorie` est la version FR (langue par défaut) ;
 * `nomCategorieEn` / `nomCategorieAr` sont des traductions optionnelles
 * stockées en colonnes I18n côté DB (cf. plan i18n trilingue).
 */
export interface CategorieMenu {
  categorieId?: number;
  hotelId?: number;
  nomCategorie: string;
  nomCategorieEn?: string;
  nomCategorieAr?: string;
  description?: string;
  ordreAffichage?: number;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;
  /** Champ dérivé renvoyé par le backend (read-only). */
  nombreArticles?: number;
}

/**
 * Filtres optionnels pour la liste des catégories.
 * `actif` permet d'isoler les catégories activées (sélecteurs en POS).
 */
export interface FiltresCategoriesMenus {
  search?: string;
  actif?: boolean;
}
