/**
 * Enveloppes d'API génériques partagées par TOUS les feature modules.
 *
 * Source de vérité unique (Tour 40ter, 2026-05-09) : centralise les définitions
 * historiquement dupliquées dans chaque `features/<X>/models/api.model.ts`.
 *
 * Stratégie de migration : les anciens fichiers locaux deviennent de simples
 * re-exports (`export * from '../../../shared/models/api.model'`) — aucun
 * import existant à modifier dans les composants/services. Migration
 * progressive sans risque pour les ~50 fichiers TS qui consomment ces types.
 *
 * ----------------------------------------------------------------------------
 * Champs : ces interfaces sont un SUPERSET des 5 variantes historiques
 * (admin, hebergement, inventory, menage, restaurant) afin de ne casser AUCUN
 * import existant. Tous les champs spécifiques à une variante sont marqués
 * optionnels.
 *
 * Convention backend Spring Boot :
 *  - `ApiResponse<T>`  : { success, message, data, errors? }
 *  - `PageResponse<T>` : projection Spring `Page<T>` aplatie côté client
 *    avec `{ content, totalElements, totalPages, number, size, ... }`.
 * ----------------------------------------------------------------------------
 */

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  /** Charge utile principale. Optionnel (nullable côté backend si erreur). */
  data?: T;
  /** Liste d'erreurs structurées (variante admin). */
  errors?: string[];
  /** Message d'erreur unique (variante restaurant/hebergement/inventory/menage). */
  error?: string;
  timestamp?: string;
  status?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  /** Page courante (0-based). */
  number: number;
  /** Présent dans toutes les variantes historiques — laissé optionnel pour
   *  rester compatible avec d'éventuelles projections allégées. */
  numberOfElements?: number;
  first: boolean;
  last: boolean;
  empty?: boolean;
}

/**
 * Paramètres de pagination/tri/recherche acceptés par les contrôleurs Spring.
 *
 * ⚠️ Trois conventions de nommage cohabitent dans la codebase actuelle :
 *  - `sortBy` + `sortDir` (admin / inventory / restaurant / menage / hebergement)
 *  - `sort` (format Spring natif `"createdAt,desc"`)
 *  - `search` (admin / inventory / restaurant / menage) vs `recherche` (hebergement)
 *
 * Tous les champs sont optionnels — chaque service compose la requête qu'il
 * connaît. À unifier dans un Tour de cleanup ultérieur.
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  /** Format Spring natif `"<field>,<dir>"` (alternative à sortBy/sortDir). */
  sort?: string;
  /** Variante anglo-saxonne (admin/inventory/restaurant/menage). */
  search?: string;
  /** Variante française (hebergement). */
  recherche?: string;
}
