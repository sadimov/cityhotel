/**
 * Modèles TS miroir des DTOs Java reporting direction.
 *   - DashboardDirectionDto      (R-DIR-001)
 *
 * Réutilise OccupationDto + CARecapDto (déjà définis dans les autres models).
 */

import { OccupationDto } from './hebergement-reports.model';
import { CARecapDto } from './finance-reports.model';

export interface DashboardDirectionDto {
  date: string;
  occupation: OccupationDto;
  caJour: CARecapDto;
  caSemaine: CARecapDto;
  nbAlertesStock: number;
  nbTachesEnCours: number;
  nbCheckInJour: number;
  nbCheckOutJour: number;
}
