/**
 * États de synthèse comptables — module comptabilite (B5 / B7).
 *
 * Sources FROZEN : DTOs sous `dto.finance.comptabilite.*` côté backend
 * (B7, 2026-05-08) :
 *  - BalanceComptableDto + LigneBalanceDto
 *  - GrandLivreDto + CompteGrandLivreDto + LigneGrandLivreDto
 *  - JournalEditionDto + EcritureJournalDto + LigneJournalDto
 *  - BilanDto + RubriqueBilanDto + LigneBilanDto
 *  - CompteResultatDto + RubriqueResultatDto + LigneResultatDto
 */

import { NatureCompte, SensNormal } from './plan-comptable.model';

// ===============================
// BALANCE COMPTABLE
// ===============================

/**
 * Ligne d'une balance comptable - agrégat par compte sur une période.
 */
export interface LigneBalanceDto {
  compteCode: string;
  compteLibelle: string;
  classe: number;
  nature: NatureCompte;
  sensNormal: SensNormal;
  totalDebit: number;
  totalCredit: number;
  soldeDebiteur: number;
  soldeCrediteur: number;
}

/**
 * DTO de la balance comptable (BalanceComptableDto cote back).
 */
export interface BalanceComptableDto {
  exerciceId: number;
  exerciceCode: string;
  dateDebut: string;
  dateFin: string;
  lignes: LigneBalanceDto[];
  totalDebit: number;
  totalCredit: number;
  totalSoldeDebiteur: number;
  totalSoldeCrediteur: number;
  generatedAt: string;
}

// ===============================
// GRAND LIVRE
// ===============================

/**
 * Ligne du grand livre - une ligne d'écriture pour un compte donné.
 *
 * `soldeProgressif` : solde courant du compte après prise en compte de
 * cette ligne (positif = débiteur, négatif = créditeur).
 */
export interface LigneGrandLivreDto {
  dateComptable: string;
  numeroEcriture: string;
  journalCode: string;
  libelleEcriture: string;
  reference?: string;
  debit: number;
  credit: number;
  soldeProgressif: number;
}

/**
 * Section grand livre pour un compte (CompteGrandLivreDto cote back).
 *
 * `reportInitial` : solde au (dateDebut - 1 jour).
 * `soldeFinal` : reportInitial + somme(debits) - somme(credits).
 */
export interface CompteGrandLivreDto {
  compteCode: string;
  compteLibelle: string;
  reportInitial: number;
  lignes: LigneGrandLivreDto[];
  totalDebit: number;
  totalCredit: number;
  soldeFinal: number;
}

/**
 * DTO du grand livre (GrandLivreDto cote back).
 */
export interface GrandLivreDto {
  dateDebut: string;
  dateFin: string;
  comptes: CompteGrandLivreDto[];
  generatedAt: string;
}

// ===============================
// EDITION JOURNAL
// ===============================

/**
 * Ligne d'écriture dans l'édition de journal (LigneJournalDto).
 */
export interface LigneJournalDto {
  compteCode: string;
  compteLibelle: string;
  debit: number;
  credit: number;
}

/**
 * Écriture dans l'édition de journal (EcritureJournalDto).
 */
export interface EcritureJournalDto {
  dateComptable: string;
  numero: string;
  libelle: string;
  reference?: string;
  lignes: LigneJournalDto[];
  totalDebitEcriture: number;
  totalCreditEcriture: number;
}

/**
 * DTO de l'édition d'un journal (JournalEditionDto cote back).
 */
export interface JournalEditionDto {
  journalId: number;
  journalCode: string;
  journalLibelle: string;
  dateDebut: string;
  dateFin: string;
  ecritures: EcritureJournalDto[];
  totalDebit: number;
  totalCredit: number;
  generatedAt: string;
}

// ===============================
// BILAN
// ===============================

/**
 * Ligne d'une rubrique de bilan (LigneBilanDto) - 1 compte = 1 ligne.
 */
export interface LigneBilanDto {
  compteCode: string;
  compteLibelle: string;
  montant: number;
}

/**
 * Rubrique du bilan (RubriqueBilanDto) - regroupement métier.
 */
export interface RubriqueBilanDto {
  code: string;
  libelle: string;
  classe: number;
  lignes: LigneBilanDto[];
  montant: number;
}

/**
 * DTO du bilan (BilanDto cote back).
 *
 * `totalActif` doit egaler `totalPassif` (resultat net inclus dans le passif).
 */
export interface BilanDto {
  exerciceId: number;
  exerciceCode: string;
  dateArrete: string;
  actif: RubriqueBilanDto[];
  totalActif: number;
  passif: RubriqueBilanDto[];
  totalPassif: number;
  resultatNet: number;
  generatedAt: string;
}

// ===============================
// COMPTE DE RESULTAT
// ===============================

/**
 * Ligne d'une rubrique de compte de résultat (LigneResultatDto).
 */
export interface LigneResultatDto {
  compteCode: string;
  compteLibelle: string;
  montant: number;
}

/**
 * Rubrique du compte de résultat (RubriqueResultatDto).
 */
export interface RubriqueResultatDto {
  code: string;
  libelle: string;
  lignes: LigneResultatDto[];
  montant: number;
}

/**
 * DTO du compte de résultat (CompteResultatDto cote back).
 *
 * `resultatNet = totalProduits - totalCharges`.
 * `margeBrute = somme(ventes 7061x-7068x) - somme(achats consommes 601x)`.
 */
export interface CompteResultatDto {
  exerciceId: number;
  exerciceCode: string;
  dateDebut: string;
  dateFin: string;
  produits: RubriqueResultatDto[];
  totalProduits: number;
  charges: RubriqueResultatDto[];
  totalCharges: number;
  resultatNet: number;
  margeBrute: number;
  generatedAt: string;
}
