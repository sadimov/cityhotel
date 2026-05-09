/**
 * Modèles Disponibilité / Planning / Calendrier — feature `hebergement`.
 *
 * Source : `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts`
 * (Tour 11, diff trois-voies — voir type-chambre.model.ts).
 */

import { Chambre } from './chambre.model';
import { StatutReservation } from './reservation.model';

/**
 * Réponse à `POST /api/hebergement/reservations/rechercher-disponibilite`.
 */
export interface DisponibiliteChambreDto {
  /** ISO `yyyy-MM-dd`. */
  dateDebut: string;
  /** ISO `yyyy-MM-dd`. */
  dateFin: string;
  chambresDisponibles: Chambre[];
  nombreChambresDisponibles: number;
  nombreChambresTotal?: number;
  tauxDisponibilite?: number;
}

/** Élément du planning (calendrier de réservations). */
export interface PlanningChambre {
  chambre: Chambre;
  reservations: PlanningReservationItem[];
}

export interface PlanningReservationItem {
  /** ISO `yyyy-MM-dd`. */
  dateDebut: string;
  /** ISO `yyyy-MM-dd`. */
  dateFin: string;
  reservationId: number;
  numeroReservation: string;
  nomClient: string;
  statut: StatutReservation;
}

/**
 * Cellule du calendrier d'occupation (vue agrégée par jour).
 * Utilisé par `<app-reservations-calendar>` pour le rendu en grille mensuelle.
 */
export interface CalendrierOccupation {
  /** ISO `yyyy-MM-dd`. */
  date: string;
  chambresOccupees: number;
  chambresDisponibles: number;
  tauxOccupation: number;
  revenus: number;
  arrivees: number;
  departs: number;
}

// ============= Statistiques & rapports =============

export interface StatistiquesHebergement {
  totalChambres: number;
  chambresDisponibles: number;
  chambresOccupees: number;
  chambresMaintenance: number;
  tauxOccupation: number;
  reservationsAujourdhui: number;
  arriveesAujourdhui: number;
  departsAujourdhui: number;
  reservationsEnCours: number;
  checkinsEnRetard: number;
  chiffreAffairesMois: number;
  chiffreAffairesAnnee: number;
  dureeSejourMoyenne: number;
}
