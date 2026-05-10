/**
 * Enveloppes d'API génériques utilisées par le module admin.
 *
 * Mêmes types conventionnels que `features/menage/models/api.model.ts`,
 * `features/restaurant/models/api.model.ts`, etc. — gardés en local au
 * feature pour éviter une dépendance croisée tant qu'aucun module
 * `shared/api/` n'a été créé.
 *
 * Convention backend Spring Boot :
 *  - `ApiResponse<T>` : { success, message, data, errors }
 *  - `PageResponse<T>` : projection Spring `Page<T>` aplatie côté client
 *    avec `{ content, totalElements, totalPages, number, size }`.
 */

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  errors?: string[];
  error?: string;
  timestamp?: string;
  status?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  search?: string;
}
