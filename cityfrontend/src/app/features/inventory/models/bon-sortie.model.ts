/**
 * Bon de sortie — module inventory.
 *
 * Statuts FROZEN (alignés avec le schéma SQL `inventory.bon_sortie.statut`).
 * Le client ne fixe jamais `userId` ni `hotelId` (lus via JWT côté serveur).
 */
export type StatutBonSortie = 'brouillon' | 'valide' | 'livre' | 'annule';

export const STATUTS_BON_SORTIE: ReadonlyArray<StatutBonSortie> = [
  'brouillon',
  'valide',
  'livre',
  'annule',
];

export interface BonSortie {
  bonSortieId?: number;
  hotelId?: number;
  numeroBon: string;
  destination: string;
  statut: StatutBonSortie;
  dateSortie: string;
  commentaires?: string;
  userId?: number;
  dateCreation?: string;
  nomUtilisateur?: string;
  lignes?: LigneBonSortie[];
  nombreLignes?: number;
  pourcentageLivraison?: number;
  peutEtreModifie?: boolean;
  peutEtreLivre?: boolean;
}

export interface LigneBonSortie {
  ligneId?: number;
  bonSortieId?: number;
  produitId: number;
  quantiteDemandee: number;
  quantiteServie?: number;
  commentaires?: string;
  nomProduit?: string;
  codeProduit?: string;
  uniteMesure?: string;
  stockDisponible?: number;
  quantiteRestante?: number;
  completeLivraison?: boolean;
  peutEtreServie?: boolean;
}

export interface FiltresBonsSortie {
  search?: string;
  statut?: StatutBonSortie;
  destination?: string;
  dateDebut?: string;
  dateFin?: string;
}
