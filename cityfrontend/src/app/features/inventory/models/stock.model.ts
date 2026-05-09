/**
 * Stocks — vues consolidées et mouvements.
 *
 * Le module inventory expose deux vues complémentaires sur les stocks :
 *  - `MouvementStock`   : journal des entrées/sorties/ajustements/pertes.
 *  - `Produit` (model)  : photo instantanée du stock courant (stockActuel,
 *                         seuilAlerte, seuilCritique, statutStock).
 *
 * La liste "stocks" du périmètre Tour 16 affiche cette photo enrichie via
 * `Produit` (lecture seule, alertes seuil).
 */

export type TypeMouvement = 'entree' | 'sortie' | 'ajustement' | 'perte';

export const TYPES_MOUVEMENT: ReadonlyArray<TypeMouvement> = [
  'entree',
  'sortie',
  'ajustement',
  'perte',
];

export interface MouvementStock {
  mouvementId?: number;
  hotelId?: number;
  produitId: number;
  typeMouvement: TypeMouvement;
  quantite: number;
  prixUnitaire?: number;
  stockAvant: number;
  stockApres: number;
  referenceDocument?: string;
  commentaire?: string;
  userId?: number;
  dateMouvement?: string;
  nomProduit?: string;
  codeProduit?: string;
  uniteMesure?: string;
  nomUtilisateur?: string;
  valeurMouvement?: number;
}

export interface FiltresMouvements {
  produitId?: number;
  typeMouvement?: TypeMouvement;
  dateDebut?: string;
  dateFin?: string;
}
