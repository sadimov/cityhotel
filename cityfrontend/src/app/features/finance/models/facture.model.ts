/**
 * Facture — module finance.
 *
 * Source FROZEN : `dto.finance.FactureDto`, `dto.finance.FactureCreateDto`,
 * `dto.finance.LigneFactureDto`, `dto.finance.LigneFactureCreateDto` côté
 * backend (audit B1+B2+B3, 2026-05-08).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — JAMAIS envoyé par le
 * client (cf. CLAUDE.md racine §6.1).
 */

/**
 * Statut comptable d'une facture (cf. entity/finance/StatutFacture.java).
 *
 * Transitions :
 *  - BROUILLON -> EMISE -> (PARTIELLEMENT_PAYEE -> PAYEE | PAYEE)
 *  - BROUILLON | EMISE -> ANNULEE
 *  - PARTIELLEMENT_PAYEE | PAYEE -> AVOIR (TypeFacture, pas un statut)
 */
export enum StatutFacture {
  BROUILLON = 'BROUILLON',
  EMISE = 'EMISE',
  PARTIELLEMENT_PAYEE = 'PARTIELLEMENT_PAYEE',
  PAYEE = 'PAYEE',
  ANNULEE = 'ANNULEE',
}

export const STATUTS_FACTURE: ReadonlyArray<StatutFacture> = [
  StatutFacture.BROUILLON,
  StatutFacture.EMISE,
  StatutFacture.PARTIELLEMENT_PAYEE,
  StatutFacture.PAYEE,
  StatutFacture.ANNULEE,
];

/**
 * Type de facture (cf. entity/finance/TypeFacture.java).
 */
export enum TypeFacture {
  FACTURE = 'FACTURE',
  AVOIR = 'AVOIR',
  PROFORMA = 'PROFORMA',
  FACTURE_FOURNISSEUR = 'FACTURE_FOURNISSEUR',
}

export const TYPES_FACTURE: ReadonlyArray<TypeFacture> = [
  TypeFacture.FACTURE,
  TypeFacture.AVOIR,
  TypeFacture.PROFORMA,
  TypeFacture.FACTURE_FOURNISSEUR,
];

/**
 * Type métier d'une ligne de facture (cf. entity/finance/TypeLigneFacture.java).
 */
export enum TypeLigneFacture {
  NUITEE = 'NUITEE',
  PRODUIT = 'PRODUIT',
  COMMANDE = 'COMMANDE',
  SERVICE = 'SERVICE',
  DIVERS = 'DIVERS',
}

export const TYPES_LIGNE_FACTURE: ReadonlyArray<TypeLigneFacture> = [
  TypeLigneFacture.NUITEE,
  TypeLigneFacture.PRODUIT,
  TypeLigneFacture.COMMANDE,
  TypeLigneFacture.SERVICE,
  TypeLigneFacture.DIVERS,
];

/**
 * DTO de sortie d'une ligne de facture (LigneFactureDto côté back).
 *
 * Les `BigDecimal` du back sont sérialisés en `number` JSON par défaut
 * (Jackson) ; en cas de besoin de précision absolue, on basculera sur
 * `string` côté front.
 */
export interface LigneFactureDto {
  ligneFactureId?: number;
  factureId?: number;
  typeLigne: TypeLigneFacture;
  nuiteeId?: number;
  produitId?: number;
  commandeId?: number;
  serviceId?: number;
  libelle: string;
  quantite: number;
  prixUnitaire: number;
  tauxTva?: number;
  montantHt?: number;
  montantTva?: number;
  montantTtc?: number;
  datePrestation?: string;
}

/**
 * DTO d'entrée pour créer une ligne de facture (LigneFactureCreateDto).
 *
 * Au moins une FK métier est attendue selon `typeLigne` (règle métier
 * vérifiée côté serveur).
 */
export interface LigneFactureCreateDto {
  typeLigne: TypeLigneFacture;
  nuiteeId?: number;
  produitId?: number;
  commandeId?: number;
  serviceId?: number;
  libelle: string;
  quantite: number;
  prixUnitaire: number;
  tauxTva?: number;
  datePrestation?: string;
}

/**
 * DTO de sortie d'une facture (FactureDto côté back).
 *
 * `hotelId` n'est PAS exposé. `montantRestant` est calculé côté serveur.
 */
export interface FactureDto {
  factureId: number;
  numeroFacture: string;
  typeFacture: TypeFacture;
  compteId?: number;
  clientId?: number;
  societeId?: number;
  reservationId?: number;
  fournisseurId?: number;
  factureReferenceId?: number;
  dateFacture: string;
  dateEcheance?: string;
  montantHt: number;
  montantTva: number;
  montantTtc: number;
  montantPaye: number;
  montantRestant: number;
  statut: StatutFacture;
  devise: string;
  commentaires?: string;
  userId?: number;
  lignes: LigneFactureDto[];
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Payload du POST /api/finance/factures (FactureCreateDto).
 *
 * `lignes` peut être vide → la facture est créée en BROUILLON, sera enrichie
 * ensuite. Si fourni, les montants sont recalculés automatiquement.
 *
 * Au moins une cible (compteId / clientId / societeId / reservationId /
 * fournisseurId) est attendue par la logique métier serveur.
 */
export interface FactureCreateDto {
  typeFacture: TypeFacture;
  compteId?: number;
  clientId?: number;
  societeId?: number;
  reservationId?: number;
  fournisseurId?: number;
  dateFacture?: string;
  dateEcheance?: string;
  devise?: string;
  commentaires?: string;
  lignes?: LigneFactureCreateDto[];
}
