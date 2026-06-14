import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import { DashboardDirectionDto } from '../models/direction-reports.model';

/**
 * Service HTTP — consultation JSON du R-DIR-001 dashboard agrégé.
 */
@Injectable({ providedIn: 'root' })
export class DirectionReportsService {
  private readonly base = `${environment.apiUrl}/api/reports/direction`;

  constructor(private readonly http: HttpClient) {}

  getDashboard(date: string): Observable<DashboardDirectionDto> {
    const params = new HttpParams().set('date', date);
    return this.http
      .get<ApiResponse<DashboardDirectionDto>>(`${this.base}/dashboard`, { params })
      .pipe(map((r) => r.data as DashboardDirectionDto));
  }
}
