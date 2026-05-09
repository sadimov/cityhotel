/**
 * Modèles Nuitée — feature `hebergement`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts`
 * (cf. Tour 11, diff trois-voies — voir type-chambre.model.ts).
 *
 * Une nuitée représente une nuit consommée par un client dans une chambre. Elle
 * est créée à la confirmation d'une réservation puis bascule en `CONSOMMEE` au
 * fil du séjour, et `FACTUREE` lorsque la facture est émise (cf. règle métier
 * Night Audit, CLAUDE.md racine §6.4).
 */

export enum StatutNuitee {
  PREVUE = 'PREVUE',
  CONSOMMEE = 'CONSOMMEE',
  FACTUREE = 'FACTUREE',
}

export interface Nuitee {
  nuiteeId?: number;
  reservationId: number;
  chambreId: number;
  /** ISO `yyyy-MM-dd`. */
  dateNuit: string;
  prixNuit: number;
  taxeSejour: number;
  statut: StatutNuitee;

  // Relations résolues serveur
  numeroChambre?: string;
  typeCode?: string;
  typeNom?: string;
  numeroReservation?: string;
  nomClientPrincipal?: string;

  // Champs calculés serveur
  montantTotal?: number;
  consommee?: boolean;
  facturee?: boolean;
  description?: string;
}
