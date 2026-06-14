/**
 * Modèles TS miroir des DTOs Java reporting finance.
 * Alignés sur :
 *   - CARecapDto                (R-FIN-001)
 *   - EncoursClientDto          (R-FIN-002)
 *   - TvaRecapDto               (R-FIN-003)
 *   - TopSocieteDto             (R-FIN-004)
 */

import { ReportPeriode } from './hebergement-reports.model';

export type { ReportPeriode };

// ── R-FIN-001 — Récap CA ────────────────────────────────────────────────────

export interface CARecapDto {
  from: string;
  to: string;
  nbFactures: number;
  caEmisHt: number;
  caEmisTva: number;
  caEmisTtc: number;
  caPayeTtc: number;
  nbPaiements: number;
  montantEncaisse: number;
  devise: string;
}

// ── R-FIN-002 — Encours clients ─────────────────────────────────────────────

export interface EncoursLigneDto {
  factureId: number;
  numeroFacture: string;
  dateFacture: string;
  dateEcheance: string;
  clientId: number;
  montantTtc: number;
  montantPaye: number;
  montantDu: number;
  ageJours: number;
  bucket: string;
}

export interface EncoursClientDto {
  reference: string;
  totalEncours: number;
  bucket0_30: number;
  bucket30_60: number;
  bucket60_90: number;
  bucket90Plus: number;
  lignes: EncoursLigneDto[];
}

// ── R-FIN-003 — TVA collectée ───────────────────────────────────────────────

export type TvaGroupBy = 'MOIS' | 'TAUX';

export interface TvaBreakdownDto {
  dimensionKey: string;
  totalHt: number;
  totalTva: number;
  totalTtc: number;
}

export interface TvaRecapDto {
  from: string;
  to: string;
  groupBy: TvaGroupBy;
  totalHt: number;
  totalTva: number;
  totalTtc: number;
  breakdown: TvaBreakdownDto[];
}

// ── R-FIN-004 — Top sociétés B2B ────────────────────────────────────────────

export interface TopSocieteDto {
  rang: number;
  societeId: number;
  societeNom: string;
  siret: string;
  nbFactures: number;
  caTtc: number;
  caPaye: number;
}
