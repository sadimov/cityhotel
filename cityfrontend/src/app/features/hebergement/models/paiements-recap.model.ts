/**
 * Modèle Recap Paiements — feature `hebergement`.
 *
 * Réponse de `GET /api/hebergement/reservations/{id}/paiements-recap`.
 *
 * Source backend : `dto/finance/RecapPaiementsReservationDto.java`,
 * `dto/finance/FactureRecapDto.java`, `dto/finance/PaiementRecapDto.java`
 * (Tour 44 Phase 1).
 *
 * Tour 44 Phase 2 (2026-05-11) : utilisé par la modale "Paiements" ouverte
 * depuis le menu contextuel du calendrier.
 */

/** Mirror de `StatutFacture` côté backend. */
export type StatutFacture =
  | 'BROUILLON'
  | 'EMISE'
  | 'PARTIELLEMENT_PAYEE'
  | 'PAYEE'
  | 'ANNULEE';

/** Mirror de `ModePaiement` côté backend (12 modes, cf. `modes_paiements.txt`). */
export type ModePaiement =
  | 'ESPECES'
  | 'CHEQUE'
  | 'CARTE_BANCAIRE'
  | 'BANKILY'
  | 'MASRIVI'
  | 'SEDAD'
  | 'CLICK'
  | 'AMANETY'
  | 'BFI_CASH'
  | 'MOOV_MONEY'
  | 'GAZAPAY'
  | 'VIREMENT';

/** Mirror de `StatutPaiement` côté backend. */
export type StatutPaiement = 'VALIDE' | 'EN_ATTENTE' | 'REFUSE' | 'ANNULE';

export interface FactureRecapDto {
  factureId: number;
  numero: string;
  statut: StatutFacture;
  /** ISO `yyyy-MM-dd`. */
  dateFacture: string;
  montantTotal: number;
  montantPaye: number;
  reste: number;
}

export interface PaiementRecapDto {
  paiementId: number;
  numeroPaiement: string;
  /** ISO `yyyy-MM-dd`. */
  datePaiement: string;
  modePaiement: ModePaiement;
  statut: StatutPaiement;
  /** Montant imputé sur la facture liée (et non le `montantTotal` du paiement). */
  montantAffecte: number;
  factureId: number;
  factureNumero: string;
}

export interface RecapPaiementsReservationDto {
  reservationId: number;
  factures: FactureRecapDto[];
  paiements: PaiementRecapDto[];
  totalGlobal: number;
  payeGlobal: number;
  resteGlobal: number;
}

/** Mirror de `TypeLigneFacture` côté backend (5 types — cf. doctrine Tour 19). */
export type TypeLigneFacture =
  | 'NUITEE'
  | 'PRODUIT'
  | 'COMMANDE'
  | 'SERVICE'
  | 'DIVERS';

/**
 * Vue tronquée d'une ligne facture pour la modale "Paiements" du calendrier
 * (Tour 45 — fix dette technique `lignesFromRecap`).
 *
 * Réponse de `GET /api/finance/factures/lignes-by-reservation/{id}`.
 *
 * Remplace l'ancien proxy `factureId → ligneFactureId` côté front : le vrai
 * `ligneFactureId` est désormais utilisé pour `POST /paiement-lignes`.
 *
 * `dateLigne` peut être `null` pour les lignes non-NUITEE sans `datePrestation`
 * — le front affiche un fallback (cf. spec).
 */
export interface LigneFactureRecapDto {
  ligneFactureId: number;
  factureId: number;
  factureNumero: string;
  description: string;
  typeLigne: TypeLigneFacture;
  /** ISO `yyyy-MM-dd` ou null si non disponible. */
  dateLigne: string | null;
  montantTtc: number;
  montantPaye: number;
  reste: number;
}

/**
 * Classe Bootstrap badge associée à chaque statut de facture (pour la modale
 * "Paiements" du calendrier). Aligné sur `STATUT_RESERVATION_BADGE_MAP`.
 */
export const STATUT_FACTURE_BADGE_MAP: Record<StatutFacture, string> = {
  BROUILLON: 'text-bg-secondary',
  EMISE: 'text-bg-info',
  PARTIELLEMENT_PAYEE: 'text-bg-warning',
  PAYEE: 'text-bg-success',
  ANNULEE: 'text-bg-danger',
};

export const STATUT_PAIEMENT_BADGE_MAP: Record<StatutPaiement, string> = {
  VALIDE: 'text-bg-success',
  EN_ATTENTE: 'text-bg-warning',
  REFUSE: 'text-bg-danger',
  ANNULE: 'text-bg-secondary',
};
