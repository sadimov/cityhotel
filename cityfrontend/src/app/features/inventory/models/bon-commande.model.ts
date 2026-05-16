/**
 * Bon de commande — module inventory.
 *
 * Statuts FROZEN (alignés avec le schéma SQL `inventory.bon_commande.statut`)
 * — cf. INVENTORY/files_back/structures_tables_schema_inventory.sql.
 * Le client ne fixe jamais `userId` ni `hotelId` (lus via JWT côté serveur).
 */
export type StatutBonCommande =
  | 'brouillon'
  | 'envoye'
  | 'confirme'
  | 'recu_partiel'
  | 'recu_complet'
  | 'annule';

/**
 * Liste des statuts exposée au template (ordre d'affichage logique du cycle
 * de vie). Utilisée par les selects + filtres.
 */
export const STATUTS_BON_COMMANDE: ReadonlyArray<StatutBonCommande> = [
  'brouillon',
  'envoye',
  'confirme',
  'recu_partiel',
  'recu_complet',
  'annule',
];

export interface BonCommande {
  bonCommandeId?: number;
  hotelId?: number;
  /** Lecture seule serveur (auto-généré via NumerotationService.BC). */
  numeroBon?: string;
  fournisseurId: number;
  /** Lecture seule serveur (initialisé à BROUILLON par le service). */
  statut?: StatutBonCommande;
  /** Lecture seule serveur (posée par le service à la création). */
  dateCommande?: string;
  dateLivraisonPrevue?: string;
  dateLivraisonReelle?: string;
  /** Lecture seule serveur (calculé depuis les lignes). */
  montantTotal?: number;
  /** Lecture seule serveur. */
  montantTva?: number;
  commentaires?: string;
  userId?: number;
  dateCreation?: string;
  nomFournisseur?: string;
  nomUtilisateur?: string;
  lignes?: LigneBonCommande[];
  nombreLignes?: number;
  pourcentageReception?: number;
  peutEtreModifie?: boolean;
  peutEtreAnnule?: boolean;
}

export interface LigneBonCommande {
  ligneId?: number;
  bonCommandeId?: number;
  produitId: number;
  quantiteCommandee: number;
  quantiteRecue?: number;
  prixUnitaire: number;
  dateReception?: string;
  nomProduit?: string;
  codeProduit?: string;
  uniteMesure?: string;
  sousTotal?: number;
  quantiteRestante?: number;
  completeReception?: boolean;
}

/**
 * Filtres optionnels pour la liste paginée des bons de commande.
 */
export interface FiltresBonsCommande {
  search?: string;
  statut?: StatutBonCommande;
  fournisseurId?: number;
  dateDebut?: string;
  dateFin?: string;
}

/**
 * Payload du POST /reception — réception (totale ou partielle) de
 * marchandises pour un bon de commande.
 */
export interface ReceptionMarchandise {
  bonCommandeId: number;
  lignes: ReceptionLigne[];
  dateReception?: string;
  commentaires?: string;
}

export interface ReceptionLigne {
  ligneId: number;
  quantiteRecue: number;
}
