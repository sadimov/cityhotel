/**
 * Modèle Chambre — feature `hebergement`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts`
 * (cf. Tour 11, diff trois-voies — voir type-chambre.model.ts).
 *
 * ⚠️ `hotelId` ne doit jamais être envoyé par le front (CLAUDE.md racine §6.1).
 */
export enum StatutChambre {
  DISPONIBLE = 'DISPONIBLE',
  OCCUPEE = 'OCCUPEE',
  MAINTENANCE = 'MAINTENANCE',
  NETTOYAGE = 'NETTOYAGE',
  HORS_SERVICE = 'HORS_SERVICE',
}

export interface Chambre {
  chambreId?: number;
  /** ⚠️ Reçu en lecture uniquement — ne jamais l'envoyer côté front. */
  hotelId?: number;
  numeroChambre: string;
  typeId: number;
  etage?: number;
  statut: StatutChambre;
  nbLits: number;
  nbPersonnesMax: number;
  /** JSON string sérialisé côté backend. */
  equipements?: string;
  description?: string;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;

  // Relations résolues serveur
  typeCode?: string;
  typeNom?: string;

  // Champs calculés serveur
  disponible?: boolean;
  occupee?: boolean;
  nomComplet?: string;
}

/**
 * Filtres de recherche pour la liste paginée des chambres.
 */
export interface FiltresChambres {
  typeId?: number;
  statut?: StatutChambre;
  etage?: number;
  disponiblesSeulement?: boolean;
  nbPersonnesMin?: number;
}
