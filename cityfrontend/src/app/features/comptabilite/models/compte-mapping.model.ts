/**
 * Mapping comptable hôtel — module comptabilite.
 *
 * Source FROZEN : `dto.finance.CompteMappingDto` +
 * `dto.finance.CompteMappingUpdateDto` côté backend (B7, 2026-05-08).
 *
 * Un mapping associe un type d'événement comptable à un compte du PCG
 * pour un hôtel donné. Le défaut codé (`defaut=true`) s'applique si aucun
 * mapping personnalisé n'a été défini.
 */

/**
 * Types d'événements comptables déclenchant une écriture
 * (cf. `entity/finance/TypeEvenementComptable.java`).
 *
 * Mappés vers des comptes du PCG mauritanien SYSCOHADA via la table
 * `finance.compte_mapping`. Chaque valeur a un défaut codé côté back.
 */
export enum TypeEvenementComptable {
  // Ventes (706xxx)
  VENTE_NUITEE_HEBERGEMENT = 'VENTE_NUITEE_HEBERGEMENT',
  VENTE_RESTAURATION = 'VENTE_RESTAURATION',
  VENTE_BAR = 'VENTE_BAR',
  VENTE_SERVICE_CHAMBRE = 'VENTE_SERVICE_CHAMBRE',
  VENTE_BLANCHISSERIE = 'VENTE_BLANCHISSERIE',
  VENTE_AUTRE_SERVICE = 'VENTE_AUTRE_SERVICE',

  // Tiers - clients
  CLIENT_PARTICULIER = 'CLIENT_PARTICULIER',
  CLIENT_SOCIETE = 'CLIENT_SOCIETE',
  CLIENT_DOUTEUX = 'CLIENT_DOUTEUX',

  // Tiers - fournisseurs
  FOURNISSEUR_ORDINAIRE = 'FOURNISSEUR_ORDINAIRE',

  // Trésorerie
  TRESORERIE_ESPECES = 'TRESORERIE_ESPECES',
  TRESORERIE_BANQUE = 'TRESORERIE_BANQUE',
  TRESORERIE_CHEQUE = 'TRESORERIE_CHEQUE',
  TRESORERIE_CARTE_BANCAIRE = 'TRESORERIE_CARTE_BANCAIRE',
  TRESORERIE_BANKILY = 'TRESORERIE_BANKILY',
  TRESORERIE_MASRIVI = 'TRESORERIE_MASRIVI',
  TRESORERIE_SEDAD = 'TRESORERIE_SEDAD',
  TRESORERIE_CLICK = 'TRESORERIE_CLICK',
  TRESORERIE_AMANETY = 'TRESORERIE_AMANETY',
  TRESORERIE_BFI_CASH = 'TRESORERIE_BFI_CASH',
  TRESORERIE_MOOV_MONEY = 'TRESORERIE_MOOV_MONEY',
  TRESORERIE_GAZAPAY = 'TRESORERIE_GAZAPAY',
  TRESORERIE_VIREMENT = 'TRESORERIE_VIREMENT',

  // TVA
  TVA_COLLECTEE = 'TVA_COLLECTEE',
  TVA_DEDUCTIBLE = 'TVA_DEDUCTIBLE',
  TVA_A_DECAISSER = 'TVA_A_DECAISSER',

  // Régularisations
  REDUCTION_ACCORDEE = 'REDUCTION_ACCORDEE',

  // Achats / Stocks
  ACHAT_MARCHANDISES = 'ACHAT_MARCHANDISES',
  STOCK_MARCHANDISES = 'STOCK_MARCHANDISES',
}

export const TYPES_EVENEMENT_COMPTABLE: ReadonlyArray<TypeEvenementComptable> = [
  TypeEvenementComptable.VENTE_NUITEE_HEBERGEMENT,
  TypeEvenementComptable.VENTE_RESTAURATION,
  TypeEvenementComptable.VENTE_BAR,
  TypeEvenementComptable.VENTE_SERVICE_CHAMBRE,
  TypeEvenementComptable.VENTE_BLANCHISSERIE,
  TypeEvenementComptable.VENTE_AUTRE_SERVICE,
  TypeEvenementComptable.CLIENT_PARTICULIER,
  TypeEvenementComptable.CLIENT_SOCIETE,
  TypeEvenementComptable.CLIENT_DOUTEUX,
  TypeEvenementComptable.FOURNISSEUR_ORDINAIRE,
  TypeEvenementComptable.TRESORERIE_ESPECES,
  TypeEvenementComptable.TRESORERIE_BANQUE,
  TypeEvenementComptable.TRESORERIE_CHEQUE,
  TypeEvenementComptable.TRESORERIE_CARTE_BANCAIRE,
  TypeEvenementComptable.TRESORERIE_BANKILY,
  TypeEvenementComptable.TRESORERIE_MASRIVI,
  TypeEvenementComptable.TRESORERIE_SEDAD,
  TypeEvenementComptable.TRESORERIE_CLICK,
  TypeEvenementComptable.TRESORERIE_AMANETY,
  TypeEvenementComptable.TRESORERIE_BFI_CASH,
  TypeEvenementComptable.TRESORERIE_MOOV_MONEY,
  TypeEvenementComptable.TRESORERIE_GAZAPAY,
  TypeEvenementComptable.TRESORERIE_VIREMENT,
  TypeEvenementComptable.TVA_COLLECTEE,
  TypeEvenementComptable.TVA_DEDUCTIBLE,
  TypeEvenementComptable.TVA_A_DECAISSER,
  TypeEvenementComptable.REDUCTION_ACCORDEE,
  TypeEvenementComptable.ACHAT_MARCHANDISES,
  TypeEvenementComptable.STOCK_MARCHANDISES,
];

/**
 * DTO de lecture d'un mapping (CompteMappingDto cote back).
 */
export interface CompteMappingDto {
  typeEvenement: TypeEvenementComptable;
  compteCode: string;
  compteLibelle: string;
  actif: boolean;
  defaut: boolean;
}

/**
 * Payload du PUT /api/finance/compte-mapping/{typeEvenement}.
 */
export interface CompteMappingUpdateDto {
  compteCode: string;
}
