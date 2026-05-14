/**
 * Plan Comptable Général (PCG) — module comptabilite.
 *
 * Source FROZEN : `dto.finance.PlanComptableGeneralDto` côté backend
 * (B7, 2026-05-08). Référentiel global partagé, lecture seule côté API.
 */

/**
 * Nature d'un compte du PCG (cf. `entity/finance/NatureCompte.java`).
 */
export enum NatureCompte {
  ACTIF = 'ACTIF',
  PASSIF = 'PASSIF',
  CHARGE = 'CHARGE',
  PRODUIT = 'PRODUIT',
  MIXTE = 'MIXTE',
}

export const NATURES_COMPTE: ReadonlyArray<NatureCompte> = [
  NatureCompte.ACTIF,
  NatureCompte.PASSIF,
  NatureCompte.CHARGE,
  NatureCompte.PRODUIT,
  NatureCompte.MIXTE,
];

/**
 * Sens normal du solde d'un compte (cf. `entity/finance/SensNormal.java`).
 */
export enum SensNormal {
  DEBITEUR = 'DEBITEUR',
  CREDITEUR = 'CREDITEUR',
  MIXTE = 'MIXTE',
}

export const SENS_NORMAL_OPTIONS: ReadonlyArray<SensNormal> = [
  SensNormal.DEBITEUR,
  SensNormal.CREDITEUR,
  SensNormal.MIXTE,
];

/**
 * Statut d'un compte du PCG (cf. `entity/finance/StatutCompteComptable.java`).
 */
export enum StatutCompteComptable {
  ACTIF = 'ACTIF',
  ARCHIVE = 'ARCHIVE',
}

export const STATUTS_COMPTE_COMPTABLE: ReadonlyArray<StatutCompteComptable> = [
  StatutCompteComptable.ACTIF,
  StatutCompteComptable.ARCHIVE,
];

/**
 * DTO de lecture d'un compte PCG (PlanComptableGeneralDto cote back).
 */
export interface PlanComptableGeneralDto {
  compteCode: string;
  libelle: string;
  classe: number;
  parentCode?: string;
  nature: NatureCompte;
  sensNormal: SensNormal;
  utilisable: boolean;
  statut: StatutCompteComptable;
}

/** Map statut -> classe Bootstrap badge. */
export const STATUT_COMPTE_BADGE_MAP: Record<StatutCompteComptable, string> = {
  ACTIF: 'text-bg-success',
  ARCHIVE: 'text-bg-secondary',
};

/** Map nature -> classe Bootstrap badge. */
export const NATURE_COMPTE_BADGE_MAP: Record<NatureCompte, string> = {
  ACTIF: 'text-bg-info',
  PASSIF: 'text-bg-primary',
  CHARGE: 'text-bg-danger',
  PRODUIT: 'text-bg-success',
  MIXTE: 'text-bg-secondary',
};
