import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Page, PageRequest } from '../../finance/models/page.model';
import { ExerciceDto } from '../models/exercice.model';

/**
 * Service HTTP — Exercices comptables (module comptabilite, B7).
 *
 * Endpoints back (B5/B7) :
 *   GET   /api/finance/exercices                paginé
 *   GET   /api/finance/exercices/{id}
 *   GET   /api/finance/exercices/current
 *   POST  /api/finance/exercices/{id}/cloturer
 *
 * Le back retourne directement les DTOs / Page Spring Data — pas
 * d'enveloppe ApiResponse côté finance/comptabilité (cf. CLAUDE.md).
 *
 * `hotelId` n'est JAMAIS transmis (lu via JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class ExercicesService {
  private readonly base = `${environment.apiUrl}/api/finance/exercices`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<ExerciceDto> {
    return this.http.get<ExerciceDto>(`${this.base}/${id}`);
  }

  current(): Observable<ExerciceDto> {
    return this.http.get<ExerciceDto>(`${this.base}/current`);
  }

  page(req: PageRequest = {}): Observable<Page<ExerciceDto>> {
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
    return this.http.get<Page<ExerciceDto>>(this.base, { params });
  }

  cloturer(id: number): Observable<ExerciceDto> {
    return this.http.post<ExerciceDto>(`${this.base}/${id}/cloturer`, {});
  }
}
