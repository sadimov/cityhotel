/**
 * Modèles TS miroir des DTOs Java reporting hébergement (Tour 41).
 * Alignés sur :
 *   - com.cityprojects.citybackend.dto.reporting.OccupationDto         (R-HEB-001)
 *   - com.cityprojects.citybackend.dto.reporting.AlosDto               (R-HEB-002)
 *   - com.cityprojects.citybackend.dto.reporting.NoShowRateDto         (R-HEB-003)
 *   - com.cityprojects.citybackend.dto.reporting.ReservationSourceDto  (R-HEB-004)
 *   - com.cityprojects.citybackend.dto.reporting.KpiReceptionDto       (R-HEB-005)
 *
 * Les `BigDecimal` Java sont sérialisés en `number` JSON ; les `LocalDate` en
 * `string` ISO `YYYY-MM-DD`.
 */

// ── R-HEB-001 — Occupation ───────────────────────────────────────────────────

export type ReportPeriode = 'JOUR' | 'SEMAINE' | 'MOIS' | 'TRIMESTRE' | 'ANNEE';

export interface TypeChambreOccupation {
  typeId: number;
  typeCode: string;
  typeNom: string;
  nbChambres: number;
  nuiteesDispo: number;
  nuiteesOccupees: number;
  tauxOccupation: number;
}

export interface OccupationDto {
  from: string;
  to: string;
  totalChambres: number;
  totalNuiteesDispo: number;
  totalNuiteesOccupees: number;
  tauxOccupationGlobal: number;
  breakdownParType: TypeChambreOccupation[];
}

// ── R-HEB-002 — ALOS ────────────────────────────────────────────────────────

export type AlosGroupBy = 'TYPE_CHAMBRE' | 'MOIS';

export interface AlosBreakdownDto {
  dimensionKey: string;
  dimensionLabel: string;
  nbReservations: number;
  totalNuits: number;
  alos: number;
}

export interface AlosDto {
  from: string;
  to: string;
  groupBy: AlosGroupBy;
  nbReservations: number;
  totalNuits: number;
  alosGlobal: number;
  breakdown: AlosBreakdownDto[];
}

// ── R-HEB-003 — No-show ─────────────────────────────────────────────────────

export type NoShowGroupBy = 'JOUR' | 'SEMAINE' | 'MOIS';

export interface NoShowBreakdownDto {
  dimensionKey: string;
  dimensionLabel: string;
  totalReservations: number;
  nbNoShow: number;
  taux: number;
}

export interface NoShowRateDto {
  from: string;
  to: string;
  groupBy: NoShowGroupBy;
  totalReservations: number;
  nbNoShow: number;
  tauxNoShowGlobal: number;
  breakdown: NoShowBreakdownDto[];
}

// ── R-HEB-004 — Sources ─────────────────────────────────────────────────────

export interface SourceBreakdownDto {
  sourceCanal: string;
  nbReservations: number;
  caMontant: number;
  pourcentage: number;
}

export interface ReservationSourceDto {
  from: string;
  to: string;
  totalReservations: number;
  caTotal: number;
  breakdown: SourceBreakdownDto[];
}

// ── R-HEB-005 — KPI Réception ───────────────────────────────────────────────

export interface KpiReceptionDto {
  date: string;
  nbCheckIn: number;
  nbCheckOut: number;
  nbWalkIn: number;
  nbReservationsActives: number;
  nbNoShow: number;
  totalChambres: number;
  nbChambresOccupees: number;
  tauxOccupationJour: number;
}
