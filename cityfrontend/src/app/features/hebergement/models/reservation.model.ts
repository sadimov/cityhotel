/**
 * Modèles Réservation — feature `hebergement`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts`
 * (cf. Tour 11, diff trois-voies — voir type-chambre.model.ts).
 *
 * ⚠️ `hotelId`, `userId` et tous les `id` ne sont **jamais** envoyés côté front
 * lors d'un create/update — le backend les déduit du JWT et de la route.
 */

export enum StatutReservation {
  EN_ATTENTE = 'EN_ATTENTE',
  CONFIRMEE = 'CONFIRMEE',
  ARRIVEE = 'ARRIVEE',
  PARTIE = 'PARTIE',
  ANNULEE = 'ANNULEE',
}

/** Lien réservation ↔ chambre (sous-période d'une réservation). */
export interface ReservationChambre {
  reservationChambreId?: number;
  reservationId?: number;
  chambreId: number;
  /** ISO `yyyy-MM-dd`. */
  dateDebut: string;
  /** ISO `yyyy-MM-dd`. */
  dateFin: string;
  prixNuit: number;

  // Relations résolues serveur
  numeroChambre?: string;
  typeCode?: string;
  typeNom?: string;
  nomCompletChambre?: string;

  // Champs calculés serveur
  nombreNuits?: number;
  montantTotal?: number;
}

/** Lien réservation ↔ client (multi-occupants, charges partagées). */
export interface ReservationClient {
  reservationClientId?: number;
  reservationId?: number;
  clientId: number;
  chambreId?: number;
  estPayant: boolean;
  pourcentageCharge: number;

  // Relations résolues serveur
  prenomClient?: string;
  nomClient?: string;
  nomCompletClient?: string;
  emailClient?: string;
  telephoneClient?: string;
  numeroChambre?: string;
}

export interface Reservation {
  reservationId?: number;
  /** ⚠️ Lecture uniquement. */
  hotelId?: number;
  numeroReservation?: string;
  clientPrincipalId: number;
  societeId?: number;
  /** ISO `yyyy-MM-dd`. */
  dateArrivee: string;
  /** ISO `yyyy-MM-dd`. */
  dateDepart: string;
  nbNuits?: number;
  nbAdultes: number;
  nbEnfants: number;
  statut?: StatutReservation;
  motifSejour?: string;
  commentaires?: string;
  reductionPourcentage?: number;
  montantTotal?: number;
  /** ⚠️ Lecture uniquement. */
  userId?: number;
  dateCreation?: string;
  dateModification?: string;

  // Relations résolues serveur
  nomClientPrincipal?: string;
  emailClient?: string;
  telephoneClient?: string;
  nomSociete?: string;
  nomUtilisateur?: string;

  // Champs calculés serveur
  nombrePersonnesTotal?: number;
  enCours?: boolean;
  annulee?: boolean;
  confirmee?: boolean;
  joursAvantArrivee?: number;
  nombreChambres?: number;

  // Détails (chargés à la demande)
  chambres?: ReservationChambre[];
  clients?: ReservationClient[];
}

// ============= Requests création / modification =============

export interface CreerReservationChambreRequest {
  chambreId: number;
  dateDebut?: string;
  dateFin?: string;
  prixNuit: number;
}

export interface CreerReservationClientRequest {
  clientId: number;
  chambreId?: number;
  estPayant: boolean;
  pourcentageCharge?: number;
}

export interface CreerReservationRequest {
  clientPrincipalId: number;
  societeId?: number;
  dateArrivee: string;
  dateDepart: string;
  nbAdultes: number;
  nbEnfants: number;
  motifSejour?: string;
  commentaires?: string;
  reductionPourcentage?: number;
  chambres: CreerReservationChambreRequest[];
  clientsAdditionnels?: CreerReservationClientRequest[];
}

export interface ModifierReservationRequest {
  clientPrincipalId?: number;
  societeId?: number;
  dateArrivee?: string;
  dateDepart?: string;
  nbAdultes?: number;
  nbEnfants?: number;
  statut?: StatutReservation;
  motifSejour?: string;
  commentaires?: string;
  reductionPourcentage?: number;
  chambres?: CreerReservationChambreRequest[];
  clientsAdditionnels?: CreerReservationClientRequest[];
}

// ============= Recherche disponibilité =============

/**
 * Body attendu par `POST /api/hebergement/reservations/rechercher-disponibilite`.
 *
 * Spec FROZEN (Tour audit hebergement B1+B2, 2026-05-07) — le backend s'aligne
 * sur ces 3 champs : `dateDebut` / `dateFin` / `nbPersonnes` (optionnel).
 * Le détail typeId / etage / capacité par tranche reste à arbitrer côté UX.
 */
export interface RechercheDisponibiliteRequest {
  /** ISO `yyyy-MM-dd`. */
  dateDebut: string;
  /** ISO `yyyy-MM-dd`. */
  dateFin: string;
  /** Capacité minimale recherchée (adultes + enfants). */
  nbPersonnes?: number;
}

// ============= Filtres liste =============

export interface FiltresReservations {
  dateArriveeDebut?: string;
  dateArriveeFin?: string;
  statut?: StatutReservation;
  clientId?: number;
  societeId?: number;
  typeId?: number;
  montantMin?: number;
  montantMax?: number;
  motifSejour?: string;
}

// ============= Helpers UI statut (Tour 40ter — H11) =============

/**
 * Classe Bootstrap badge associée à chaque statut de réservation.
 * Utilisé par `reservations-list` (liste tableau, badges arrondis Bootstrap 5).
 *
 * Centralisé ici (Tour 40ter, 2026-05-09) pour éviter la duplication du
 * `switch` entre `reservations-list.component.ts` et tout autre composant
 * futur affichant un badge statut (détail, filtres, dashboard).
 */
export const STATUT_RESERVATION_BADGE_MAP: Record<StatutReservation, string> = {
  [StatutReservation.EN_ATTENTE]: 'text-bg-warning',
  [StatutReservation.CONFIRMEE]: 'text-bg-info',
  [StatutReservation.ARRIVEE]: 'text-bg-success',
  [StatutReservation.PARTIE]: 'text-bg-secondary',
  [StatutReservation.ANNULEE]: 'text-bg-danger',
};

/**
 * Classe CSS BEM `reservations-calendar__chip--<statut>` pour les chips du
 * composant `reservations-calendar`. Conservée distincte de la map badges
 * Bootstrap car le SCSS du calendrier définit ses propres palettes.
 */
export const STATUT_RESERVATION_CHIP_MAP: Record<StatutReservation, string> = {
  [StatutReservation.EN_ATTENTE]: 'reservations-calendar__chip--en-attente',
  [StatutReservation.CONFIRMEE]: 'reservations-calendar__chip--confirmee',
  [StatutReservation.ARRIVEE]: 'reservations-calendar__chip--arrivee',
  [StatutReservation.PARTIE]: 'reservations-calendar__chip--partie',
  [StatutReservation.ANNULEE]: 'reservations-calendar__chip--annulee',
};

/**
 * Renvoie la clé i18n du libellé statut (`hebergement.statut.<statut>` en
 * minuscules). Fallback sur `EN_ATTENTE` si le statut est indéterminé.
 *
 * Centralise la convention partagée par `reservations-list`,
 * `reservations-calendar` et `check-in-form`.
 */
export function statutReservationKey(statut: StatutReservation | undefined): string {
  const s = statut ?? StatutReservation.EN_ATTENTE;
  return 'hebergement.statut.' + s.toLowerCase();
}
