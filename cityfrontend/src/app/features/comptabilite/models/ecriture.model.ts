/**
 * Écriture comptable en partie double — module comptabilite.
 *
 * Source FROZEN : `dto.finance.EcritureComptableDto`,
 * `dto.finance.EcritureComptableCreateDto`,
 * `dto.finance.LigneEcritureDto`,
 * `dto.finance.LigneEcritureCreateDto`,
 * `dto.finance.ContrePassationDto` côté backend (B7, 2026-05-08).
 */

/**
 * Statut d'une écriture comptable
 * (cf. `entity/finance/StatutEcriture.java`).
 *
 * Cycle de vie : BROUILLON -> VALIDEE -> CONTRE_PASSEE.
 */
export enum StatutEcriture {
  BROUILLON = 'BROUILLON',
  VALIDEE = 'VALIDEE',
  CONTRE_PASSEE = 'CONTRE_PASSEE',
}

export const STATUTS_ECRITURE: ReadonlyArray<StatutEcriture> = [
  StatutEcriture.BROUILLON,
  StatutEcriture.VALIDEE,
  StatutEcriture.CONTRE_PASSEE,
];

/**
 * Sens d'une ligne en partie double
 * (cf. `entity/finance/SensLigne.java`).
 */
export enum SensLigne {
  DEBIT = 'DEBIT',
  CREDIT = 'CREDIT',
}

export const SENS_LIGNE_OPTIONS: ReadonlyArray<SensLigne> = [
  SensLigne.DEBIT,
  SensLigne.CREDIT,
];

/**
 * DTO de lecture d'une ligne d'écriture (LigneEcritureDto cote back).
 */
export interface LigneEcritureDto {
  id?: number;
  ordre: number;
  compteCode: string;
  libelle?: string;
  sens: SensLigne;
  montant: number;
  compteAuxiliaireRef?: string;
}

/**
 * DTO de création d'une ligne d'écriture (LigneEcritureCreateDto).
 *
 * Le montant est toujours positif — le sens porte la directionnalité.
 */
export interface LigneEcritureCreateDto {
  ordre?: number;
  compteCode: string;
  libelle?: string;
  sens: SensLigne;
  montant: number;
  compteAuxiliaireRef?: string;
}

/**
 * DTO de lecture d'une écriture comptable (EcritureComptableDto).
 *
 * `journalCode` / `journalLibelle` / `exerciceCode` sont dénormalisés
 * pour éviter un round-trip côté front.
 */
export interface EcritureComptableDto {
  id: number;
  numero: string;
  dateComptable: string;
  datePiece?: string;
  journalId: number;
  journalCode: string;
  journalLibelle: string;
  exerciceId: number;
  exerciceCode: string;
  libelle: string;
  reference?: string;
  statut: StatutEcriture;
  contrePasseeParId?: number;
  ecritureSourceId?: number;
  totalDebit: number;
  totalCredit: number;
  lignes: LigneEcritureDto[];
  createdAt?: string;
  createdBy?: string;
}

/**
 * Payload du POST /api/finance/ecritures (EcritureComptableCreateDto).
 *
 * Le service valide :
 *  - au moins 2 lignes
 *  - somme des debits == somme des credits (tolerance 0.01 MRU)
 *  - chaque compteCode existe et est utilisable dans le PCG
 *  - l'exercice contenant dateComptable est OUVERT
 *  - le journal existe et est actif
 * Si datePiece est null, le service le force a dateComptable.
 */
export interface EcritureComptableCreateDto {
  dateComptable: string;
  datePiece?: string;
  journalCode: string;
  libelle: string;
  reference?: string;
  lignes: LigneEcritureCreateDto[];
}

/**
 * Payload du POST /api/finance/ecritures/{id}/contre-passer
 * (ContrePassationDto cote back).
 */
export interface ContrePassationDto {
  motif: string;
}

/** Map statut -> classe Bootstrap badge. */
export const STATUT_ECRITURE_BADGE_MAP: Record<StatutEcriture, string> = {
  BROUILLON: 'text-bg-secondary',
  VALIDEE: 'text-bg-success',
  CONTRE_PASSEE: 'text-bg-warning',
};
