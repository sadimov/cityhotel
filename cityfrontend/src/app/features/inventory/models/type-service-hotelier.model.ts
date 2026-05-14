/**
 * Type de service hôtelier — module inventory (Tour 51 Phase A).
 *
 * Référentiel parent regroupant les services proposés par l'hôtel
 * (blanchisserie, mini-bar, navette, spa, etc.). Multi-tenant :
 * `hotelId` lu via JWT côté serveur — jamais envoyé par le client
 * (cf. CLAUDE.md racine §6.1).
 */
export interface TypeServiceHotelier {
  typeServiceId?: number;
  hotelId?: number;
  codeType: string;
  nomType: string;
  description?: string;
  actif?: boolean;
  dateCreation?: string;
  nombreServices?: number;
}

export interface FiltresTypesServices {
  search?: string;
  actif?: boolean;
}
