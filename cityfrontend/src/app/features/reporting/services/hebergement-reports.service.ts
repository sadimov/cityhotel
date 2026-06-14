import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  AlosDto,
  AlosGroupBy,
  KpiReceptionDto,
  NoShowGroupBy,
  NoShowRateDto,
  OccupationDto,
  ReportPeriode,
  ReservationSourceDto,
} from '../models/hebergement-reports.model';

/**
 * Service HTTP — consultation JSON des 5 rapports R-HEB-001..005.
 *
 * <p>Les controllers backend wrappent toutes leurs réponses dans
 * {@code ApiResponse<T>} (cf. {@code shared/models/api.model.ts}). Le pipe
 * {@code map(r => r.data)} extrait la charge utile attendue.</p>
 *
 * <p>Les endpoints d'export (XLSX/PDF) restent gérés par
 * {@code ReportsDownloadService}.</p>
 */
@Injectable({ providedIn: 'root' })
export class HebergementReportsService {
  private readonly base = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  // R-HEB-001
  getOccupation(periode: ReportPeriode, from?: string, to?: string): Observable<OccupationDto> {
    let params = new HttpParams().set('periode', periode);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http
      .get<ApiResponse<OccupationDto>>(`${this.base}/occupation`, { params })
      .pipe(map((r) => r.data as OccupationDto));
  }

  // R-HEB-002
  getAlos(from: string, to: string, groupBy: AlosGroupBy): Observable<AlosDto> {
    const params = new HttpParams().set('from', from).set('to', to).set('groupBy', groupBy);
    return this.http
      .get<ApiResponse<AlosDto>>(`${this.base}/hebergement/alos`, { params })
      .pipe(map((r) => r.data as AlosDto));
  }

  // R-HEB-003
  getNoShowRate(from: string, to: string, groupBy: NoShowGroupBy): Observable<NoShowRateDto> {
    const params = new HttpParams().set('from', from).set('to', to).set('groupBy', groupBy);
    return this.http
      .get<ApiResponse<NoShowRateDto>>(`${this.base}/hebergement/no-show-rate`, { params })
      .pipe(map((r) => r.data as NoShowRateDto));
  }

  // R-HEB-004
  getSources(from: string, to: string): Observable<ReservationSourceDto> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .get<ApiResponse<ReservationSourceDto>>(`${this.base}/hebergement/sources`, { params })
      .pipe(map((r) => r.data as ReservationSourceDto));
  }

  // R-HEB-005
  getKpiReception(date: string): Observable<KpiReceptionDto> {
    const params = new HttpParams().set('date', date);
    return this.http
      .get<ApiResponse<KpiReceptionDto>>(`${this.base}/hebergement/kpi-reception`, { params })
      .pipe(map((r) => r.data as KpiReceptionDto));
  }
}
