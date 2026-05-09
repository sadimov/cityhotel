/**
 * Modèle Societe — feature `clients`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_clients_frontend.ts`
 * (Tour 8 — voir note dans `client.model.ts`).
 *
 * ⚠️ Idem `Client` : `hotelId` est en lecture seule côté front. Le backend
 * l'injecte depuis le JWT (CLAUDE.md racine §6.1).
 */
export interface Societe {
  societeId?: number;
  /** ⚠️ Reçu en lecture uniquement — ne jamais l'envoyer côté front. */
  hotelId?: number;
  societeNom: string;
  siret?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  contactPrincipal?: string;
  actif?: boolean;
  dateCreation?: string;
  dateModification?: string;

  // ────── Champs calculés serveur (lecture seule) ──────
  nombreClients?: number;
  hotelNom?: string;
}

/**
 * DTO de création — `hotelId` retiré (backend le tire du JWT).
 */
export type SocieteCreate = Omit<
  Societe,
  | 'societeId'
  | 'hotelId'
  | 'dateCreation'
  | 'dateModification'
  | 'nombreClients'
  | 'hotelNom'
>;

export type SocieteUpdate = SocieteCreate;
