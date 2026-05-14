/**
 * Modèle Tarification — feature `hebergement`.
 *
 * Réponse de `GET /api/hebergement/tarifs-chambre/calculer`.
 *
 * Source backend : `dto/hebergement/MontantCalculDto.java` +
 * `dto/hebergement/MontantCalculDetailDto.java` (Tour 44 Phase 1).
 *
 * Tour 44 Phase 2 (2026-05-11) : consommation calendrier — proposition de prix
 * réelle lors de la création de réservation (remplace l'estimation
 * `TypeChambre.prixBase × nbNuits`).
 *
 * NB : pas de TVA en palier 1 — `montantTtc === montantHt` côté backend tant
 * que la TVA n'est pas activée. On expose les deux champs pour rester aligné
 * sur le contrat serveur, et le front affiche le `montantTtc`.
 */

/**
 * Origine du prix retourné pour une nuit :
 *  - `tarif`     : tarif saisonnier explicite (TarifChambre actif sur la date)
 *  - `fallback`  : `TypeChambre.prixBase` (aucun tarif saisonnier applicable)
 */
export type MontantCalculOrigine = 'tarif' | 'fallback';

export interface MontantCalculDetailDto {
  /** ISO `yyyy-MM-dd`. */
  date: string;
  /** Prix unitaire de la nuit (MRU). */
  prix: number;
  origine: MontantCalculOrigine;
}

export interface MontantCalculDto {
  typeChambreId: number;
  totalNuits: number;
  montantHt: number;
  montantTtc: number;
  detail: MontantCalculDetailDto[];
}
