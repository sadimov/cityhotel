import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  ChargePersonnelDto,
  RecapTacheDto,
  TacheGroupBy,
} from '../models/menage-reports.model';

/**
 * Service HTTP — consultation JSON des rapports R-MEN-001..002.
 */
@Injectable({ providedIn: 'root' })
export class MenageReportsService {
  private readonly base = `${environment.apiUrl}/api/reports/menage`;

  constructor(private readonly http: HttpClient) {}

  getRecapTaches(from: string, to: string, groupBy: TacheGroupBy): Observable<RecapTacheDto> {
    const params = new HttpParams().set('from', from).set('to', to).set('groupBy', groupBy);
    return this.http
      .get<ApiResponse<RecapTacheDto>>(`${this.base}/recap-taches`, { params })
      .pipe(map((r) => r.data as RecapTacheDto));
  }

  getChargePersonnel(from: string, to: string): Observable<ChargePersonnelDto> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .get<ApiResponse<ChargePersonnelDto>>(`${this.base}/charge-personnel`, { params })
      .pipe(map((r) => r.data as ChargePersonnelDto));
  }
}
