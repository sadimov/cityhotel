/**
 * Journal comptable — module comptabilite.
 *
 * Source FROZEN : `dto.finance.JournalComptableDto`,
 * `dto.finance.JournalComptableCreateDto`,
 * `dto.finance.JournalComptableUpdateDto` côté backend (B7, 2026-05-08).
 */

/**
 * Famille fonctionnelle d'un journal (cf. `entity/finance/TypeJournal.java`).
 */
export enum TypeJournal {
  VENTE = 'VENTE',
  ACHAT = 'ACHAT',
  TRESORERIE = 'TRESORERIE',
  OPERATION_DIVERSE = 'OPERATION_DIVERSE',
  AVOIR = 'AVOIR',
}

export const TYPES_JOURNAL: ReadonlyArray<TypeJournal> = [
  TypeJournal.VENTE,
  TypeJournal.ACHAT,
  TypeJournal.TRESORERIE,
  TypeJournal.OPERATION_DIVERSE,
  TypeJournal.AVOIR,
];

/**
 * DTO de lecture d'un journal comptable (JournalComptableDto cote back).
 */
export interface JournalComptableDto {
  id: number;
  code: string;
  libelle: string;
  type: TypeJournal;
  actif: boolean;
}

/**
 * DTO de creation d'un journal comptable
 * (JournalComptableCreateDto cote back).
 *
 * Le code (3-5 caracteres alphanumeriques majuscules) est l'identifiant
 * stable. Pas modifiable apres creation (sert de discriminant pour la
 * numerotation des ecritures).
 */
export interface JournalComptableCreateDto {
  code: string;
  libelle: string;
  type: TypeJournal;
}

/**
 * DTO de mise a jour d'un journal comptable
 * (JournalComptableUpdateDto cote back). Le code n'est pas modifiable.
 */
export interface JournalComptableUpdateDto {
  libelle: string;
  type: TypeJournal;
}

/** Regex de validation cote front pour le code (alignee avec le back). */
export const JOURNAL_CODE_PATTERN = /^[A-Z0-9]{1,5}$/;
