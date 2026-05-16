import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import { FactureCreateDto, FactureDto } from '../models/facture.model';
import { Page, PageRequest } from '../models/page.model';

/**
 * Service HTTP — Factures (module finance).
 *
 * Spec FROZEN — alignée sur `FactureController` côté backend (audit
 * B1+B2+B3, 2026-05-08) :
 *   - GET    /api/finance/factures/{id}                          → FactureDto
 *   - GET    /api/finance/factures                               → Page<FactureDto>
 *   - POST   /api/finance/factures                               → FactureDto (201)
 *   - POST   /api/finance/factures/{id}/emettre                  → FactureDto
 *   - POST   /api/finance/factures/{id}/annuler                  → FactureDto
 *   - POST   /api/finance/factures/from-reservation/{resaId}     → FactureDto (201)
 *
 * ⚠️ `ApiResponseBodyAdvice` (post-v1.0.0) wrappe TOUTES les réponses
 * controller en `{data, status, ...}`. On unwrap via `.pipe(map)` avec
 * fallback `r.data ?? r` pour compat ascendante. La régression "paiements
 * cassés" venait du fait que `findById` retournait `{data: FactureDto}` mais
 * le composant lisait `f.montantRestant` direct → undefined → validator
 * `min(0.01)` bloquait le bouton ENCAISSER.
 *
 * ⚠️ `hotelId` n'est jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class FacturesService {
  private readonly base = `${environment.apiUrl}/api/finance/factures`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<FactureDto> {
    return this.http
      .get<ApiResponse<FactureDto>>(`${this.base}/${id}`)
      .pipe(map((r) => (r?.data ?? (r as unknown as FactureDto))));
  }

  page(req: PageRequest = {}): Observable<Page<FactureDto>> {
    let params = new HttpParams();
    if (req.page != null) params = params.set('page', String(req.page));
    if (req.size != null) params = params.set('size', String(req.size));
    if (req.sort) {
      if (Array.isArray(req.sort)) {
        for (const s of req.sort) params = params.append('sort', s);
      } else {
        params = params.set('sort', req.sort);
      }
    }
    return this.http
      .get<ApiResponse<Page<FactureDto>>>(this.base, { params })
      .pipe(map((r) => (r?.data ?? (r as unknown as Page<FactureDto>))));
  }

  create(dto: FactureCreateDto): Observable<FactureDto> {
    return this.http
      .post<ApiResponse<FactureDto>>(this.base, dto)
      .pipe(map((r) => (r?.data ?? (r as unknown as FactureDto))));
  }

  emettre(id: number): Observable<FactureDto> {
    return this.http
      .post<ApiResponse<FactureDto>>(`${this.base}/${id}/emettre`, {})
      .pipe(map((r) => (r?.data ?? (r as unknown as FactureDto))));
  }

  annuler(id: number): Observable<FactureDto> {
    return this.http
      .post<ApiResponse<FactureDto>>(`${this.base}/${id}/annuler`, {})
      .pipe(map((r) => (r?.data ?? (r as unknown as FactureDto))));
  }

  fromReservation(reservationId: number): Observable<FactureDto> {
    return this.http
      .post<ApiResponse<FactureDto>>(
        `${this.base}/from-reservation/${reservationId}`,
        {},
      )
      .pipe(map((r) => (r?.data ?? (r as unknown as FactureDto))));
  }

  /**
   * Téléchargement PDF — `ApiResponseBodyAdvice` ne wrappe pas les binaires
   * (cf. cas 4 byte[]/Resource). On garde le Blob brut.
   */
  downloadPdf(factureId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${factureId}/pdf`, {
      responseType: 'blob',
    });
  }
}
