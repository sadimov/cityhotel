/**
 * Paiement — module finance.
 *
 * Source FROZEN : `dto.finance.PaiementDto`, `dto.finance.PaiementCreateDto`,
 * `dto.finance.AffectationPaiementDto`, `dto.finance.AffectationCreateDto`
 * côté backend (audit B1+B2+B3, 2026-05-08).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — JAMAIS envoyé par le
 * client.
 */

/**
 * Statut d'un paiement encaissé (cf. entity/finance/StatutPaiement.java).
 */
export enum StatutPaiement {
  VALIDE = 'VALIDE',
  EN_ATTENTE = 'EN_ATTENTE',
  REFUSE = 'REFUSE',
  ANNULE = 'ANNULE',
}

export const STATUTS_PAIEMENT: ReadonlyArray<StatutPaiement> = [
  StatutPaiement.VALIDE,
  StatutPaiement.EN_ATTENTE,
  StatutPaiement.REFUSE,
  StatutPaiement.ANNULE,
];

/**
 * Modes de paiement supportés (cf. entity/finance/ModePaiement.java +
 * `modes_paiements.txt` racine).
 */
export enum ModePaiement {
  ESPECES = 'ESPECES',
  CHEQUE = 'CHEQUE',
  CARTE_BANCAIRE = 'CARTE_BANCAIRE',
  BANKILY = 'BANKILY',
  MASRIVI = 'MASRIVI',
  SEDAD = 'SEDAD',
  CLICK = 'CLICK',
  AMANETY = 'AMANETY',
  BFI_CASH = 'BFI_CASH',
  MOOV_MONEY = 'MOOV_MONEY',
  GAZAPAY = 'GAZAPAY',
  VIREMENT = 'VIREMENT',
}

export const MODES_PAIEMENT: ReadonlyArray<ModePaiement> = [
  ModePaiement.ESPECES,
  ModePaiement.CHEQUE,
  ModePaiement.CARTE_BANCAIRE,
  ModePaiement.BANKILY,
  ModePaiement.MASRIVI,
  ModePaiement.SEDAD,
  ModePaiement.CLICK,
  ModePaiement.AMANETY,
  ModePaiement.BFI_CASH,
  ModePaiement.MOOV_MONEY,
  ModePaiement.GAZAPAY,
  ModePaiement.VIREMENT,
];

/**
 * Modes pour lesquels la `referencePaiement` est obligatoire (chèque,
 * carte, wallets mobiles, virement). Espèces : pas de référence.
 */
export const MODES_PAIEMENT_AVEC_REFERENCE: ReadonlyArray<ModePaiement> = [
  ModePaiement.CHEQUE,
  ModePaiement.CARTE_BANCAIRE,
  ModePaiement.BANKILY,
  ModePaiement.MASRIVI,
  ModePaiement.SEDAD,
  ModePaiement.CLICK,
  ModePaiement.AMANETY,
  ModePaiement.BFI_CASH,
  ModePaiement.MOOV_MONEY,
  ModePaiement.GAZAPAY,
  ModePaiement.VIREMENT,
];

/**
 * DTO de sortie d'une affectation paiement → facture
 * (AffectationPaiementDto côté back).
 */
export interface AffectationPaiementDto {
  affectationId: number;
  paiementId: number;
  factureId: number;
  montantAffecte: number;
  dateAffectation: string;
}

/**
 * DTO d'entrée pour ventiler un paiement existant à une facture
 * (AffectationCreateDto côté back).
 */
export interface AffectationCreateDto {
  factureId: number;
  montantAffecte: number;
}

/**
 * DTO de sortie d'un paiement (PaiementDto côté back).
 */
export interface PaiementDto {
  paiementId: number;
  numeroPaiement: string;
  compteId?: number;
  montantTotal: number;
  devise: string;
  modePaiement: ModePaiement;
  referencePaiement?: string;
  datePaiement: string;
  statut: StatutPaiement;
  commentaires?: string;
  userId?: number;
  affectations: AffectationPaiementDto[];
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Payload du POST /api/finance/paiements (PaiementCreateDto).
 *
 * Si `factureId` est fourni, le serveur crée automatiquement une affectation
 * pour `montantTotal` (encaissement direct sur facture).
 */
export interface PaiementCreateDto {
  compteId?: number;
  factureId?: number;
  montantTotal: number;
  devise?: string;
  modePaiement: ModePaiement;
  referencePaiement?: string;
  datePaiement?: string;
  commentaires?: string;
}
