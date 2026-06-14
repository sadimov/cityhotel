/**
 * Modèles TS miroir des DTOs Java reporting inventory.
 *   - StockAlertDto              (R-INV-001)
 *   - MouvementValoriseDto       (R-INV-002)
 *   - BcPendantDto + Rotation    (R-INV-003)
 */

export type TypeMouvementStock = 'ENTREE' | 'SORTIE' | 'PERTE' | 'AJUSTEMENT';
export type StatutBonCommande = 'brouillon' | 'envoye' | 'confirme' | 'recu_partiel' | 'recu_complet' | 'annule';

// R-INV-001
export interface StockAlertDto {
  produitId: number;
  codeProduit: string;
  nomProduit: string;
  uniteMesure: string;
  stockActuel: number;
  seuilAlerte: number;
  seuilCritique: number;
  ecart: number;
  statut: 'CRITIQUE' | 'ALERTE';
  valeurManquante: number;
}

// R-INV-002
export interface MouvementLigneDto {
  mouvementId: number;
  date: string;
  produitId: number;
  codeProduit: string;
  nomProduit: string;
  typeMouvement: TypeMouvementStock;
  quantite: number;
  prixUnitaire: number;
  valeur: number;
  referenceDocument: string;
}

export interface MouvementValoriseDto {
  from: string;
  to: string;
  typeFilter: TypeMouvementStock | null;
  nbMouvements: number;
  valeurEntrees: number;
  valeurSorties: number;
  lignes: MouvementLigneDto[];
}

// R-INV-003a
export interface BcPendantDto {
  bonCommandeId: number;
  numeroBc: string;
  fournisseurId: number;
  statut: StatutBonCommande;
  dateCommande: string;
  dateLivraisonPrevue: string;
  ageJours: number;
  montantTotal: number;
}

// R-INV-003b
export interface RotationProduitDto {
  produitId: number;
  codeProduit: string;
  nomProduit: string;
  totalSorties: number;
  stockActuel: number;
  rotation: number;
}
