import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  JournalCaisseDto,
  TicketMarginDto,
  TopArticleDto,
} from '../models/restaurant-reports.model';

/**
 * Service HTTP — consultation JSON des rapports R-RES-001..003.
 */
@Injectable({ providedIn: 'root' })
export class RestaurantReportsService {
  private readonly base = `${environment.apiUrl}/api/reports/restaurant`;

  constructor(private readonly http: HttpClient) {}

  getJournalCaisse(date: string): Observable<JournalCaisseDto> {
    const params = new HttpParams().set('date', date);
    return this.http
      .get<ApiResponse<JournalCaisseDto>>(`${this.base}/journal-caisse`, { params })
      .pipe(map((r) => r.data as JournalCaisseDto));
  }

  getTopArticles(from: string, to: string, limit = 20): Observable<TopArticleDto> {
    const params = new HttpParams()
      .set('from', from).set('to', to).set('limit', String(limit));
    return this.http
      .get<ApiResponse<TopArticleDto>>(`${this.base}/top-articles`, { params })
      .pipe(map((r) => r.data as TopArticleDto));
  }

  getTicketMoyen(from: string, to: string): Observable<TicketMarginDto> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .get<ApiResponse<TicketMarginDto>>(`${this.base}/ticket-moyen`, { params })
      .pipe(map((r) => r.data as TicketMarginDto));
  }
}
