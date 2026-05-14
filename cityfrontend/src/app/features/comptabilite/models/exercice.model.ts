/**
 * Exercice comptable — module comptabilite.
 *
 * Source FROZEN : `dto.finance.ExerciceDto` côté backend (B7, 2026-05-08).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — JAMAIS envoyé par le
 * client (cf. CLAUDE.md racine §6.1).
 */

/**
 * Statut d'un exercice comptable
 * (cf. `entity/finance/StatutExercice.java`).
 *
 * Transitions autorisees :
 *  OUVERT --> EN_CLOTURE --> CLOTURE  (cloture definitive, pas de retour).
 */
export enum StatutExercice {
  OUVERT = 'OUVERT',
  EN_CLOTURE = 'EN_CLOTURE',
  CLOTURE = 'CLOTURE',
}

export const STATUTS_EXERCICE: ReadonlyArray<StatutExercice> = [
  StatutExercice.OUVERT,
  StatutExercice.EN_CLOTURE,
  StatutExercice.CLOTURE,
];

/**
 * DTO de lecture d'un exercice comptable (ExerciceDto cote back).
 */
export interface ExerciceDto {
  id: number;
  code: string;
  dateDebut: string;
  dateFin: string;
  statut: StatutExercice;
  dateCloture?: string;
  clotureBy?: string;
}

/** Map statut -> classe Bootstrap badge. */
export const STATUT_EXERCICE_BADGE_MAP: Record<StatutExercice, string> = {
  OUVERT: 'text-bg-success',
  EN_CLOTURE: 'text-bg-warning',
  CLOTURE: 'text-bg-secondary',
};
