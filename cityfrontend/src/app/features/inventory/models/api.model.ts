/**
 * Enveloppes d'API génériques utilisées par le module inventory.
 *
 * Mêmes types que `features/clients/models/...` et `features/hebergement/models/api.model.ts`
 * — gardés en local au feature pour éviter une dépendance croisée tant qu'aucun
 * module `shared/api/` n'a été créé. À factoriser ultérieurement.
 */

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
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
