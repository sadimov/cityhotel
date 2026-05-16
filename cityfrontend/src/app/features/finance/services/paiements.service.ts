import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import { Page, PageRequest } from '../models/page.model';
import {
  AffectationCreateDto,
  PaiementCreateDto,
  PaiementDto,
} from '../models/paiement.model';

/**
 * Service HTTP — Paiements (module finance).
 *
 * Spec FROZEN — alignée sur `PaiementController` côté backend (audit
 * B1+B2+B3, 2026-05-08) :
 *   - GET    /api/finance/paiements/{id}                  → PaiementDto
 *   - GET    /api/finance/paiements                       → Page<PaiementDto>
 *   - POST   /api/finance/paiements                       → PaiementDto (201)
 *   - POST   /api/finance/paiements/{id}/affecter         → PaiementDto
 *   - POST   /api/finance/paiements/{id}/annuler          → PaiementDto
 *
 * ⚠️ `ApiResponseBodyAdvice` (post-v1.0.0) wrappe TOUTES les réponses
 * controller en `{data, status, ...}`. On unwrap via `.pipe(map)` avec
 * fallback `r.data ?? r` pour compat ascendante.
 *
 * ⚠️ `hotelId` n'est jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class PaiementsService {
  private readonly base = `${environment.apiUrl}/api/finance/paiements`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<PaiementDto> {
    return this.http
      .get<ApiResponse<PaiementDto>>(`${this.base}/${id}`)
      .pipe(map((r) => (r?.data ?? (r as unknown as PaiementDto))));
  }

  page(req: PageRequest = {}): Observable<Page<PaiementDto>> {
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
      .get<ApiResponse<Page<PaiementDto>>>(this.base, { params })
      .pipe(map((r) => (r?.data ?? (r as unknown as Page<PaiementDto>))));
  }

  create(dto: PaiementCreateDto): Observable<PaiementDto> {
    return this.http
      .post<ApiResponse<PaiementDto>>(this.base, dto)
      .pipe(map((r) => (r?.data ?? (r as unknown as PaiementDto))));
  }

  affecter(
    paiementId: number,
    affectations: AffectationCreateDto[],
  ): Observable<PaiementDto> {
    return this.http
      .post<ApiResponse<PaiementDto>>(
        `${this.base}/${paiementId}/affecter`,
        affectations,
      )
      .pipe(map((r) => (r?.data ?? (r as unknown as PaiementDto))));
  }

  annuler(id: number): Observable<PaiementDto> {
    return this.http
      .post<ApiResponse<PaiementDto>>(`${this.base}/${id}/annuler`, {})
      .pipe(map((r) => (r?.data ?? (r as unknown as PaiementDto))));
  }
}
