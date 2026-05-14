/**
 * Configuration TVA + Déclarations TVA — module comptabilite (B4 / B7).
 *
 * Sources FROZEN : `dto.finance.TauxTvaConfigDto`,
 * `dto.finance.TauxTvaConfigUpdateDto`,
 * `dto.finance.DeclarationTvaDto`,
 * `dto.finance.DeclarationTvaCreateDto` côté backend (B7, 2026-05-08).
 */

/**
 * Types de services hôteliers du point de vue TVA
 * (cf. `entity/finance/TypeServiceTva.java`).
 */
export enum TypeServiceTva {
  HEBERGEMENT_NUITEE = 'HEBERGEMENT_NUITEE',
  RESTAURATION = 'RESTAURATION',
  BAR = 'BAR',
  SERVICE_CHAMBRE = 'SERVICE_CHAMBRE',
  BLANCHISSERIE = 'BLANCHISSERIE',
  AUTRE_SERVICE_HOTELIER = 'AUTRE_SERVICE_HOTELIER',
  ACHAT_MARCHANDISES = 'ACHAT_MARCHANDISES',
}

export const TYPES_SERVICE_TVA: ReadonlyArray<TypeServiceTva> = [
  TypeServiceTva.HEBERGEMENT_NUITEE,
  TypeServiceTva.RESTAURATION,
  TypeServiceTva.BAR,
  TypeServiceTva.SERVICE_CHAMBRE,
  TypeServiceTva.BLANCHISSERIE,
  TypeServiceTva.AUTRE_SERVICE_HOTELIER,
  TypeServiceTva.ACHAT_MARCHANDISES,
];

/**
 * Statut d'une déclaration TVA
 * (cf. `entity/finance/StatutDeclarationTva.java`).
 */
export enum StatutDeclarationTva {
  BROUILLON = 'BROUILLON',
  VALIDEE = 'VALIDEE',
}

export const STATUTS_DECLARATION_TVA: ReadonlyArray<StatutDeclarationTva> = [
  StatutDeclarationTva.BROUILLON,
  StatutDeclarationTva.VALIDEE,
];

/**
 * DTO de lecture d'une configuration TVA (TauxTvaConfigDto cote back).
 */
export interface TauxTvaConfigDto {
  typeService: TypeServiceTva;
  taux: number;
  actif: boolean;
  libelle: string;
  defaut: boolean;
}

/**
 * Payload du PUT /api/finance/tva/config/{typeService}
 * (TauxTvaConfigUpdateDto cote back).
 *
 * `actif` et `libelle` sont nullables : si null, la valeur courante est
 * conservée côté serveur.
 */
export interface TauxTvaConfigUpdateDto {
  taux: number;
  actif?: boolean | null;
  libelle?: string | null;
}

/**
 * DTO de lecture d'une déclaration TVA (DeclarationTvaDto cote back).
 */
export interface DeclarationTvaDto {
  id: number;
  dateDebut: string;
  dateFin: string;
  totalTvaCollectee: number;
  totalTvaDeductible: number;
  totalTvaADecaisser: number;
  statut: StatutDeclarationTva;
  exerciceId?: number;
  ecritureLiquidationId?: number;
  dateValidation?: string;
  valideePar?: string;
}

/**
 * Payload du POST /api/finance/tva/declarations
 * (DeclarationTvaCreateDto cote back).
 */
export interface DeclarationTvaCreateDto {
  dateDebut: string;
  dateFin: string;
}

/** Map statut -> classe Bootstrap badge. */
export const STATUT_DECLARATION_TVA_BADGE_MAP: Record<StatutDeclarationTva, string> = {
  BROUILLON: 'text-bg-secondary',
  VALIDEE: 'text-bg-success',
};
