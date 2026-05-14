import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Page, PageRequest } from '../../finance/models/page.model';
import {
  JournalComptableCreateDto,
  JournalComptableDto,
  JournalComptableUpdateDto,
} from '../models/journal.model';

/**
 * Service HTTP — Journaux comptables (B7).
 *
 * Endpoints back :
 *   GET   /api/finance/journaux                paginé
 *   GET   /api/finance/journaux/{id}
 *   POST  /api/finance/journaux                body=JournalComptableCreateDto
 *   PUT   /api/finance/journaux/{id}           body=JournalComptableUpdateDto
 *   POST  /api/finance/journaux/{id}/deactivate
 *   POST  /api/finance/journaux/{id}/reactivate
 */
@Injectable({ providedIn: 'root' })
export class JournauxService {
  private readonly base = `${environment.apiUrl}/api/finance/journaux`;

  constructor(private readonly http: HttpClient) {}

  findById(id: number): Observable<JournalComptableDto> {
    return this.http.get<JournalComptableDto>(`${this.base}/${id}`);
  }

  page(req: PageRequest = {}): Observable<Page<JournalComptableDto>> {
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
    return this.http.get<Page<JournalComptableDto>>(this.base, { params });
  }

  create(dto: JournalComptableCreateDto): Observable<JournalComptableDto> {
    return this.http.post<JournalComptableDto>(this.base, dto);
  }

  update(
    id: number,
    dto: JournalComptableUpdateDto,
  ): Observable<JournalComptableDto> {
    return this.http.put<JournalComptableDto>(`${this.base}/${id}`, dto);
  }

  deactivate(id: number): Observable<JournalComptableDto> {
    return this.http.post<JournalComptableDto>(
      `${this.base}/${id}/deactivate`,
      {},
    );
  }

  reactivate(id: number): Observable<JournalComptableDto> {
    return this.http.post<JournalComptableDto>(
      `${this.base}/${id}/reactivate`,
      {},
    );
  }
}
