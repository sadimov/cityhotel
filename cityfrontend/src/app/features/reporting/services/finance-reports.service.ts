import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  CARecapDto,
  EncoursClientDto,
  ReportPeriode,
  TopSocieteDto,
  TvaGroupBy,
  TvaRecapDto,
} from '../models/finance-reports.model';

/**
 * Service HTTP — consultation JSON des 4 rapports R-FIN-001..004.
 *
 * Endpoints :
 *   - GET /api/reports/ca                              (R-FIN-001)
 *   - GET /api/reports/finance/encours-clients         (R-FIN-002)
 *   - GET /api/reports/finance/tva-recap               (R-FIN-003)
 *   - GET /api/reports/finance/top-societes            (R-FIN-004)
 *
 * Réponses backend wrappées dans ApiResponse<T> → déballées via map(r => r.data).
 */
@Injectable({ providedIn: 'root' })
export class FinanceReportsService {
  private readonly base = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  // R-FIN-001
  getCA(periode: ReportPeriode, from?: string, to?: string): Observable<CARecapDto> {
    let params = new HttpParams().set('periode', periode);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http
      .get<ApiResponse<CARecapDto>>(`${this.base}/ca`, { params })
      .pipe(map((r) => r.data as CARecapDto));
  }

  // R-FIN-002
  getEncoursClients(reference?: string): Observable<EncoursClientDto> {
    let params = new HttpParams();
    if (reference) params = params.set('reference', reference);
    return this.http
      .get<ApiResponse<EncoursClientDto>>(`${this.base}/finance/encours-clients`, { params })
      .pipe(map((r) => r.data as EncoursClientDto));
  }

  // R-FIN-003
  getTvaRecap(from: string, to: string, groupBy: TvaGroupBy): Observable<TvaRecapDto> {
    const params = new HttpParams().set('from', from).set('to', to).set('groupBy', groupBy);
    return this.http
      .get<ApiResponse<TvaRecapDto>>(`${this.base}/finance/tva-recap`, { params })
      .pipe(map((r) => r.data as TvaRecapDto));
  }

  // R-FIN-004
  getTopSocietes(from: string, to: string, limit = 10): Observable<TopSocieteDto[]> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('limit', String(limit));
    return this.http
      .get<ApiResponse<TopSocieteDto[]>>(`${this.base}/finance/top-societes`, { params })
      .pipe(map((r) => r.data ?? []));
  }
}
