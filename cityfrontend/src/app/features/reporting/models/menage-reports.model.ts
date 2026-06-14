/**
 * Modèles TS miroir des DTOs Java reporting ménage.
 *   - RecapTacheDto              (R-MEN-001)
 *   - ChargePersonnelDto         (R-MEN-002)
 */

export type TacheGroupBy = 'JOUR' | 'TYPE_TACHE' | 'STATUT';

export interface RecapBreakdownDto {
  dimensionKey: string;
  nbTaches: number;
}

export interface RecapTacheDto {
  from: string;
  to: string;
  groupBy: TacheGroupBy;
  totalTaches: number;
  breakdown: RecapBreakdownDto[];
}

export interface ChargeLigneDto {
  personnelId: number;
  nbAssignees: number;
  nbTerminees: number;
  dureeTotaleMin: number;
  tauxCompletion: number;
}

export interface ChargePersonnelDto {
  from: string;
  to: string;
  personnels: ChargeLigneDto[];
}
