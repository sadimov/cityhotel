/**
 * Modèle Client — feature `clients`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_clients_frontend.ts`
 * (Tour 8 — diff trois-voies : les 4 instances `models_services_clients_frontend.ts`
 * dans /FINANCE, /HEBERGEMENT/files_front, /HEBERGEMENT/COMPONENT_CALENDAR et
 * /RESTAURANT/point-vente sont byte-for-byte identiques. Aucun choix arbitré
 * sur le contenu — choix par défaut COMPONENT_CALENDAR conforme aux consignes).
 *
 * ⚠️ `hotelId` reste exposé pour rétro-compatibilité avec les payloads serveur
 * legacy (DTO d'entrée), mais le **front ne doit jamais l'envoyer** lors d'un
 * `create()` / `update()` — le backend l'extrait du JWT (CLAUDE.md racine §6.1).
 */
export interface Client {
  clientId?: number;
  /** ⚠️ Reçu en lecture uniquement — ne jamais l'envoyer côté front. */
  hotelId?: number;
  numeroClient?: string;
  prenom: string;
  nom: string;
  nationaliteId?: number;
  telephone?: string;
  email?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  typeIdentificationId?: number;
  numeroIdentification?: string;
  /** Format ISO `yyyy-MM-dd`. */
  dateNaissance?: string;
  societeId?: number;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;

  // ────── Champs calculés serveur (lecture seule) ──────
  nomComplet?: string;
  age?: number;
  nationaliteLibelle?: string;
  typeIdentificationLibelle?: string;
  societeNom?: string;
  hotelNom?: string;
}

/**
 * DTO de création — `hotelId` retiré (backend le tire du JWT).
 */
export type ClientCreate = Omit<
  Client,
  | 'clientId'
  | 'hotelId'
  | 'dateCreation'
  | 'dateModification'
  | 'nomComplet'
  | 'age'
  | 'nationaliteLibelle'
  | 'typeIdentificationLibelle'
  | 'societeNom'
  | 'hotelNom'
>;

/**
 * DTO de mise à jour — identique à `ClientCreate` côté payload accepté serveur.
 */
export type ClientUpdate = ClientCreate;

/**
 * Statistiques agrégées clients + sociétés pour un hôtel.
 */
export interface StatistiquesClient {
  totalClients: number;
  clientsActifs: number;
  clientsInactifs: number;
  clientsAvecSociete: number;
  clientsSansSociete: number;
  totalSocietes: number;
  societesActives: number;
  societesInactives: number;
  pourcentageClientsActifs: number;
  pourcentageSocietesActives: number;
  moyenneClientsParSociete: number;
}

/**
 * Donnée de référentiel (nationalité, type d'identification, secteur d'activité).
 */
export interface DonneesReferentielles {
  refId: number;
  categorie: string;
  code: string;
  libelle: string;
  libelleEn?: string;
  libelleAr?: string;
  ordreAffichage: number;
  actif: boolean;
}

/**
 * Enveloppe générique des réponses API (ApiResponse côté backend Spring Boot).
 */
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
  timestamp?: string;
  status?: number;
}

/**
 * Page Spring (utilisée pour les listes paginées).
 */
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

/**
 * Requête de pagination — utilitaire pour les composants liste.
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  recherche?: string;
}
