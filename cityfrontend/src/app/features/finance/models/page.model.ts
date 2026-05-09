/**
 * Modèle d'une page Spring Data — exposée brute par le backend `finance`
 * (cf. `FactureController.findAll` / `PaiementController.findAll`).
 *
 * Aucune enveloppe `ApiResponse` côté finance : la réponse JSON est
 * directement la sérialisation d'un `Page<T>` Jackson.
 *
 * À déplacer dans un futur `shared/api/page.model.ts` lorsque les autres
 * features (clients, hebergement, inventory) auront migré dans le même sens.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty?: boolean;
}

/**
 * Paramètres standards d'une requête paginée Spring Data.
 *
 * Convention `sort` : `champ,asc` ou `champ,desc` (Spring Data) — multi-tri
 * possible en répétant le paramètre.
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string | string[];
}
