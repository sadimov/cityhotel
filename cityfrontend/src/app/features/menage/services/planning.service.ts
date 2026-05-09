import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  FiltresPlanning,
  Planning,
  PlanningCreate,
  PlanningUpdate,
} from '../models/planning.model';

/**
 * Service HTTP — Plannings du personnel de ménage.
 *
 * Spec (cf. `MENAGE/endpoints_module_menage.txt` §Planning) :
 *   - GET    /api/menage/planning                     — page (filtres)
 *   - GET    /api/menage/planning/aujourd-hui         — raccourci jour J
 *   - GET    /api/menage/planning/date/{date}         — par date
 *   - GET    /api/menage/planning/personnel/{pid}     — par personnel + date
 *   - GET    /api/menage/planning/{id}                — read
 *   - POST   /api/menage/planning                     — create
 *   - PUT    /api/menage/planning/{id}                — update
 *   - DELETE /api/menage/planning/{id}                — delete
 *
 * ⚠️ `hotelId` n'est JAMAIS transmis (JWT côté serveur, cf. CLAUDE.md §6.1).
 */
@Injectable({ providedIn: 'root' })
export class PlanningService {
  private readonly base = `${environment.apiUrl}/api/menage/planning`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste paginée des plannings avec filtres optionnels.
   *
   * Convention d'enveloppe `ApiResponse<PageResponse<Planning>>` alignée
   * sur les autres services du module (taches, personnels).
   */
  page(
    filtres: FiltresPlanning = {},
    page = 0,
    size = 10,
    sortBy = 'dateTravail',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<Planning>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.date) {
      params = params.set('date', filtres.date);
    }
    if (filtres.personnelId != null) {
      params = params.set('personnelId', String(filtres.personnelId));
    }
    if (filtres.disponible !== undefined) {
      params = params.set('disponible', String(filtres.disponible));
    }
    return this.http
      .get<ApiResponse<PageResponse<Planning>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Planning>));
  }

  findById(id: number): Observable<Planning> {
    return this.http
      .get<ApiResponse<Planning>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Planning));
  }

  /** Plannings d'un personnel — `date` optionnelle (sinon backend = today). */
  findByPersonnelId(personnelId: number, date?: string): Observable<Planning[]> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http
      .get<ApiResponse<Planning[]>>(`${this.base}/personnel/${personnelId}`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  findByDate(date: string): Observable<Planning[]> {
    return this.http
      .get<ApiResponse<Planning[]>>(`${this.base}/date/${encodeURIComponent(date)}`)
      .pipe(map((r) => r.data ?? []));
  }

  findAujourdhui(): Observable<Planning[]> {
    return this.http
      .get<ApiResponse<Planning[]>>(`${this.base}/aujourd-hui`)
      .pipe(map((r) => r.data ?? []));
  }

  create(dto: PlanningCreate): Observable<Planning> {
    return this.http
      .post<ApiResponse<Planning>>(this.base, dto)
      .pipe(map((r) => r.data as Planning));
  }

  update(id: number, dto: PlanningUpdate): Observable<Planning> {
    return this.http
      .put<ApiResponse<Planning>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Planning));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
