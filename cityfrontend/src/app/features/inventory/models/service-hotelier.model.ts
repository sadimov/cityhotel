/**
 * Service hôtelier — module inventory (Tour 51 Phase A).
 *
 * Service vendu à un client (blanchisserie, mini-bar consommé, navette
 * aéroport, etc.). Rattaché à un `TypeServiceHotelier`. Multi-tenant :
 * `hotelId` lu via JWT côté serveur — jamais envoyé par le client.
 */
export interface ServiceHotelier {
  serviceId?: number;
  hotelId?: number;
  typeServiceId: number;
  codeService: string;
  nomService: string;
  description?: string;
  prixUnitaire: number;
  uniteMesure?: string;
  estFacturable?: boolean;
  actif?: boolean;
  dateCreation?: string;
  nomType?: string;
  codeType?: string;
}

export interface FiltresServicesHoteliers {
  search?: string;
  typeServiceId?: number;
  actif?: boolean;
}
