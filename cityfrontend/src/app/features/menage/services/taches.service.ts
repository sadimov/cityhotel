import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  AssignerTacheRequest,
  FiltresTaches,
  Tache,
  TerminerTacheRequest,
} from '../models/tache.model';

/**
 * Service HTTP — Tâches de ménage.
 *
 * Spec (cf. `MENAGE/endpoints_module_menage.txt` §Tâches) :
 *   - GET    /api/menage/taches                       — page (filtres)
 *   - GET    /api/menage/taches/aujourd-hui           — raccourci jour J
 *   - GET    /api/menage/taches/date/{date}           — par date
 *   - GET    /api/menage/taches/personnel/{pid}       — par personnel + date
 *   - GET    /api/menage/taches/en-cours              — raccourci
 *   - GET    /api/menage/taches/en-retard             — raccourci
 *   - GET    /api/menage/taches/non-assignees         — raccourci
 *   - GET    /api/menage/taches/rechercher?terme=     — recherche textuelle
 *   - GET    /api/menage/taches/{id}                  — read
 *   - POST   /api/menage/taches                       — create
 *   - PUT    /api/menage/taches/{id}                  — update
 *   - DELETE /api/menage/taches/{id}                  — delete
 *   - PUT    /api/menage/taches/{id}/assigner         — assignation personnel
 *   - PUT    /api/menage/taches/{id}/commencer        — début effectif
 *   - PUT    /api/menage/taches/{id}/terminer         — fin + rapport
 *
 * ⚠️ `hotelId` n'est JAMAIS transmis (JWT).
 */
@Injectable({ providedIn: 'root' })
export class TachesService {
  private readonly base = `${environment.apiUrl}/api/menage/taches`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste paginée des tâches avec filtres optionnels.
   *
   * Les raccourcis booléens (`enCours`, `enRetard`, `nonAssignees`) du
   * filtre routent vers les endpoints dédiés ; les autres filtres
   * combinés passent par l'endpoint racine.
   */
  page(
    filtres: FiltresTaches = {},
    page = 0,
    size = 10,
    sortBy = 'datePlanifiee',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<Tache>> {
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
    if (filtres.chambreId != null) {
      params = params.set('chambreId', String(filtres.chambreId));
    }
    if (filtres.statutId != null) {
      params = params.set('statutId', String(filtres.statutId));
    }
    if (filtres.typeNettoyage) {
      params = params.set('typeNettoyage', filtres.typeNettoyage);
    }
    if (filtres.priorite != null) {
      params = params.set('priorite', String(filtres.priorite));
    }
    if (filtres.enCours) {
      params = params.set('enCours', 'true');
    }
    if (filtres.enRetard) {
      params = params.set('enRetard', 'true');
    }
    if (filtres.nonAssignees) {
      params = params.set('nonAssignees', 'true');
    }
    return this.http
      .get<ApiResponse<PageResponse<Tache>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Tache>));
  }

  findAujourdhui(): Observable<Tache[]> {
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/aujourd-hui`)
      .pipe(map((r) => r.data ?? []));
  }

  findByDate(date: string): Observable<Tache[]> {
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/date/${encodeURIComponent(date)}`)
      .pipe(map((r) => r.data ?? []));
  }

  findByPersonnel(personnelId: number, date?: string): Observable<Tache[]> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/personnel/${personnelId}`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  findEnCours(): Observable<Tache[]> {
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/en-cours`)
      .pipe(map((r) => r.data ?? []));
  }

  findEnRetard(): Observable<Tache[]> {
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/en-retard`)
      .pipe(map((r) => r.data ?? []));
  }

  findNonAssignees(): Observable<Tache[]> {
    return this.http
      .get<ApiResponse<Tache[]>>(`${this.base}/non-assignees`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<Tache> {
    return this.http
      .get<ApiResponse<Tache>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Tache));
  }

  create(dto: Tache): Observable<Tache> {
    return this.http
      .post<ApiResponse<Tache>>(this.base, dto)
      .pipe(map((r) => r.data as Tache));
  }

  update(id: number, dto: Tache): Observable<Tache> {
    return this.http
      .put<ApiResponse<Tache>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Tache));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  assigner(id: number, payload: AssignerTacheRequest): Observable<Tache> {
    return this.http
      .put<ApiResponse<Tache>>(`${this.base}/${id}/assigner`, payload)
      .pipe(map((r) => r.data as Tache));
  }

  /** Début effectif — backend pose `heureDebutReelle = now()`. */
  commencer(id: number): Observable<Tache> {
    return this.http
      .put<ApiResponse<Tache>>(`${this.base}/${id}/commencer`, {})
      .pipe(map((r) => r.data as Tache));
  }

  /**
   * Fin effective + rapport — backend pose `heureFinReelle = now()`,
   * `dureeMinutes` recalculée en base.
   */
  terminer(id: number, payload: TerminerTacheRequest = {}): Observable<Tache> {
    return this.http
      .put<ApiResponse<Tache>>(`${this.base}/${id}/terminer`, payload)
      .pipe(map((r) => r.data as Tache));
  }
}
