import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Page, PageRequest } from '../../finance/models/page.model';
import { PlanComptableGeneralDto } from '../models/plan-comptable.model';

/**
 * Service HTTP — Plan Comptable Général (lecture seule, B7).
 *
 * Endpoints back :
 *   GET /api/finance/plan-comptable               paginé
 *   GET /api/finance/plan-comptable/{compteCode}
 *
 * Référentiel global partagé entre hôtels — aucune isolation tenant
 * appliquée côté back (mais sécurisé via @PreAuthorize).
 */
@Injectable({ providedIn: 'root' })
export class PlanComptableService {
  private readonly base = `${environment.apiUrl}/api/finance/plan-comptable`;

  constructor(private readonly http: HttpClient) {}

  findByCode(compteCode: string): Observable<PlanComptableGeneralDto> {
    return this.http.get<PlanComptableGeneralDto>(`${this.base}/${compteCode}`);
  }

  page(req: PageRequest = {}): Observable<Page<PlanComptableGeneralDto>> {
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
    return this.http.get<Page<PlanComptableGeneralDto>>(this.base, { params });
  }
}
